package com.financetracker.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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

  Optional<Transaction> findByIdAndUserId(long id, long userId);

  /** How many of the user's transactions reference any of the given categories. */
  long countByUserIdAndCategoryIdIn(long userId, Collection<Long> categoryIds);

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
          GROUP BY t.category_id
          """,
      nativeQuery = true)
  List<CategorySumRow> sumByCategory(
      @Param("userId") long userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
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
}
