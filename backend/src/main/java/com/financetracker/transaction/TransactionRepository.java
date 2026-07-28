package com.financetracker.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Transaction persistence. Every finder/aggregate is scoped by {@code userId}; aggregation runs in
 * SQL (per CLAUDE.md §9) but its correctness is pinned by fixed-fixture tests. Base conversion uses
 * the per-row locked rate: {@code round(amount_minor * rate_to_base)}, summed. Dynamic list filters
 * use {@link JpaSpecificationExecutor} (Criteria) rather than nullable JPQL params — the latter
 * trips Postgres' "could not determine data type" on untyped {@code IS NULL} comparisons.
 */
public interface TransactionRepository
    extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

  Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(long id, long userId);

  /** A trashed row (for restore / delete-forever). */
  Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNotNull(long id, long userId);

  /** All of the user's active transactions, oldest first — for data export / backup. */
  List<Transaction> findByUserIdAndDeletedAtIsNullOrderByDateAscIdAsc(long userId);

  /** The user's trashed transactions, most-recently-deleted first. */
  Page<Transaction> findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(
      long userId, Pageable pageable);

  /** How many active transactions reference any of the given categories. */
  long countByUserIdAndDeletedAtIsNullAndCategoryIdIn(long userId, Collection<Long> categoryIds);

  /** The user's active uncategorized transactions of the given types (for re-running rules). */
  List<Transaction> findByUserIdAndDeletedAtIsNullAndCategoryIdIsNullAndTypeIn(
      long userId, Collection<TransactionType> types);

  /** Purge trashed rows past the retention cutoff. */
  @Modifying
  @Query("DELETE FROM Transaction t WHERE t.deletedAt < :cutoff")
  int deleteByDeletedAtBefore(@Param("cutoff") Instant cutoff);

  /** Dedupe hashes already present for an account, for skipping duplicates on CSV import. */
  @Query(
      "SELECT t.dedupeHash FROM Transaction t "
          + "WHERE t.userId = :userId AND t.accountId = :accountId AND t.deletedAt IS NULL")
  List<String> findDedupeHashesByUserIdAndAccountId(
      @Param("userId") long userId, @Param("accountId") long accountId);

  /**
   * Income/expense totals for a date range, in base (reporting) minor units. Each row is converted
   * with its own locked rate and rounded before summing, then grouped by type. Transfers excluded.
   */
  @Query(
      value =
          """
          SELECT t.type AS type,
                 COALESCE(SUM(round(t.amount_minor * t.rate_to_base)), 0) AS baseMinor
          FROM transactions t
          WHERE t.user_id = :userId
            AND t.date BETWEEN :from AND :to
            AND t.type IN ('income', 'expense')
            AND t.deleted_at IS NULL
          GROUP BY t.type
          """,
      nativeQuery = true)
  List<SummaryRow> summarize(
      @Param("userId") long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  /**
   * Net activity for one account in its native-currency minor units: income in, expense out,
   * transfers out of the account and into it via the counter side. (Assumes same-currency
   * transfers, matching the prototype.)
   */
  @Query(
      value =
          """
          SELECT COALESCE(SUM(
              CASE
                  WHEN t.account_id = :accountId AND t.type = 'income'  THEN  t.amount_minor
                  WHEN t.account_id = :accountId AND t.type = 'expense' THEN -t.amount_minor
                  WHEN t.account_id = :accountId AND t.type = 'transfer' THEN -t.amount_minor
                  WHEN t.counter_account_id = :accountId AND t.type = 'transfer' THEN t.amount_minor
                  ELSE 0
              END), 0)
          FROM transactions t
          WHERE t.user_id = :userId
            AND (t.account_id = :accountId OR t.counter_account_id = :accountId)
            AND t.deleted_at IS NULL
          """,
      nativeQuery = true)
  long accountActivityMinor(@Param("userId") long userId, @Param("accountId") long accountId);

  /**
   * Base totals grouped by category for one kind over a date range (null category = uncategorized).
   * Folded into the two-level breakdown in tested Java.
   */
  @Query(
      value =
          """
          SELECT t.category_id AS categoryId,
                 COALESCE(SUM(round(t.amount_minor * t.rate_to_base)), 0) AS baseMinor,
                 COUNT(*) AS txnCount
          FROM transactions t
          WHERE t.user_id = :userId
            AND t.date BETWEEN :from AND :to
            AND t.type = :type
            AND t.deleted_at IS NULL
          GROUP BY t.category_id
          """,
      nativeQuery = true)
  List<CategorySumRow> sumByCategory(
      @Param("userId") long userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("type") String type);

  /**
   * Base totals grouped by time bucket and type (income/expense) over a date range. {@code fmt} is
   * a Postgres {@code to_char} pattern chosen by the interval ({@code 'YYYY-MM'} month, {@code
   * 'IYYY-IW'} ISO week). Empty buckets are absent — the service zero-fills the range.
   *
   * <p>Groups by ordinal ({@code GROUP BY 1, 2}) so {@code :fmt} appears exactly once: referenced
   * twice, Hibernate emits two positional params and Postgres then sees the SELECT and GROUP BY
   * {@code to_char} expressions as different and rejects the grouping.
   */
  @Query(
      value =
          """
          SELECT to_char(t.date, cast(:fmt as text)) AS period,
                 t.type AS type,
                 COALESCE(SUM(round(t.amount_minor * t.rate_to_base)), 0) AS baseMinor
          FROM transactions t
          WHERE t.user_id = :userId
            AND t.date BETWEEN :from AND :to
            AND t.type IN ('income', 'expense')
            AND t.deleted_at IS NULL
          GROUP BY 1, 2
          """,
      nativeQuery = true)
  List<PeriodSumRow> sumByPeriod(
      @Param("userId") long userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("fmt") String fmt);

  /**
   * Base totals grouped by time bucket and category (null = uncategorized) for one kind, over a
   * date range. Folded into per-period category stacks (subcategories rolled to parents) in tested
   * Java. Groups by ordinal so {@code :fmt} appears once (see {@link #sumByPeriod}).
   */
  @Query(
      value =
          """
          SELECT to_char(t.date, cast(:fmt as text)) AS period,
                 t.category_id AS categoryId,
                 COALESCE(SUM(round(t.amount_minor * t.rate_to_base)), 0) AS baseMinor
          FROM transactions t
          WHERE t.user_id = :userId
            AND t.date BETWEEN :from AND :to
            AND t.type = :type
            AND t.deleted_at IS NULL
          GROUP BY 1, 2
          """,
      nativeQuery = true)
  List<PeriodCategorySumRow> sumByPeriodAndCategory(
      @Param("userId") long userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("fmt") String fmt,
      @Param("type") String type);

  /** Native projection for {@link #summarize}. */
  interface SummaryRow {
    String getType();

    BigDecimal getBaseMinor();
  }

  /** Projection for {@link #sumByCategory}; {@code categoryId} is null for uncategorized spend. */
  interface CategorySumRow {
    Long getCategoryId();

    BigDecimal getBaseMinor();

    long getTxnCount();
  }

  /** Projection for {@link #sumByPeriod}: base total per (period, type). */
  interface PeriodSumRow {
    String getPeriod();

    String getType();

    BigDecimal getBaseMinor();
  }

  /** Projection for {@link #sumByPeriodAndCategory}; {@code categoryId} null = uncategorized. */
  interface PeriodCategorySumRow {
    String getPeriod();

    Long getCategoryId();

    BigDecimal getBaseMinor();
  }
}
