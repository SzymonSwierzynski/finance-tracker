package com.financetracker.recurring;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryKind;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.error.ConflictException;
import com.financetracker.common.error.NotFoundException;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.common.hash.DedupeHash;
import com.financetracker.fx.RateResolver;
import com.financetracker.recurring.dto.CreateRecurringRequest;
import com.financetracker.recurring.dto.RecurringResponse;
import com.financetracker.recurring.dto.RunRecurringResponse;
import com.financetracker.recurring.dto.UpdateRecurringRequest;
import com.financetracker.transaction.Transaction;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Recurring template business logic: ownership + category/type invariants on the template, and
 * materializing due templates into real transactions. Each materialized transaction locks its rate
 * to base at materialization time (CLAUDE.md §7) and links back via {@code recurringId}.
 */
@Service
public class RecurringTransactionService {

  /**
   * Cap catch-up per template per run so a very old daily template can't create a runaway batch.
   */
  private static final int MAX_MATERIALIZE_PER_RUN = 500;

  /**
   * "Today" for recurring schedules is anchored to UTC, not the server's default zone (CLAUDE.md
   * §3: never depend on server timezone for business logic), so "due today" is deterministic per
   * host.
   */
  private static final ZoneId SCHEDULE_ZONE = ZoneOffset.UTC;

  private final RecurringTransactionRepository recurringRepository;
  private final AccountRepository accountRepository;
  private final CategoryRepository categoryRepository;
  private final TransactionRepository transactionRepository;
  private final RateResolver rateResolver;

  public RecurringTransactionService(
      RecurringTransactionRepository recurringRepository,
      AccountRepository accountRepository,
      CategoryRepository categoryRepository,
      TransactionRepository transactionRepository,
      RateResolver rateResolver) {
    this.recurringRepository = recurringRepository;
    this.accountRepository = accountRepository;
    this.categoryRepository = categoryRepository;
    this.transactionRepository = transactionRepository;
    this.rateResolver = rateResolver;
  }

  @Transactional
  public RecurringResponse create(long userId, CreateRecurringRequest request) {
    if (request.type() == TransactionType.TRANSFER) {
      throw new UnprocessableEntityException("Recurring transfers are not supported.");
    }
    Account account =
        accountRepository
            .findByIdAndUserId(request.accountId(), userId)
            .orElseThrow(() -> NotFoundException.of("Account", request.accountId()));
    String currency =
        StringUtils.hasText(request.currency())
            ? request.currency().toUpperCase(Locale.ROOT)
            : account.getCurrency();
    // Fail fast if the currency has no usable rate; the actual rate is locked per materialization.
    rateResolver.resolve(userId, currency, null);
    if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
      throw new UnprocessableEntityException("endDate must not be before startDate.");
    }

    RecurringTransaction template = new RecurringTransaction();
    template.setUserId(userId);
    template.setAccountId(request.accountId());
    template.setCategoryId(validateCategory(userId, request.type(), request.categoryId()));
    template.setAmountMinor(request.amountMinor());
    template.setType(request.type());
    template.setCurrency(currency);
    template.setDescription(request.description() == null ? "" : request.description().trim());
    template.setNote(request.note() == null ? "" : request.note().trim());
    template.setFrequency(request.frequency());
    template.setIntervalCount(request.intervalCount() == null ? 1 : request.intervalCount());
    template.setStartDate(request.startDate());
    template.setEndDate(request.endDate());
    template.setNextRunDate(request.startDate());
    template.setActive(true);
    return toResponse(recurringRepository.save(template));
  }

  @Transactional(readOnly = true)
  public List<RecurringResponse> list(long userId) {
    return recurringRepository.findByUserIdOrderByNextRunDateAsc(userId).stream()
        .map(RecurringTransactionService::toResponse)
        .toList();
  }

  @Transactional
  public RecurringResponse update(long userId, long id, UpdateRecurringRequest request) {
    RecurringTransaction template = requireOwned(userId, id);
    requireCurrentVersion(template, request.version());
    if (request.amountMinor() != null) {
      template.setAmountMinor(request.amountMinor());
    }
    if (request.description() != null) {
      template.setDescription(request.description().trim());
    }
    if (request.note() != null) {
      template.setNote(request.note().trim());
    }
    if (request.active() != null) {
      template.setActive(request.active());
    }
    if (request.endDate() != null) {
      if (request.endDate().isBefore(template.getStartDate())) {
        throw new UnprocessableEntityException("endDate must not be before startDate.");
      }
      template.setEndDate(request.endDate());
    }
    return toResponse(recurringRepository.saveAndFlush(template));
  }

  @Transactional
  public void delete(long userId, long id) {
    recurringRepository.delete(requireOwned(userId, id));
  }

  /** Materialize the user's due templates on demand (the user-triggered {@code /recurring/run}). */
  @Transactional
  public RunRecurringResponse run(long userId) {
    LocalDate today = LocalDate.now(SCHEDULE_ZONE);
    int created = 0;
    for (RecurringTransaction template :
        recurringRepository.findByUserIdAndActiveTrueAndNextRunDateLessThanEqual(userId, today)) {
      created += materializeOne(template, today);
    }
    return new RunRecurringResponse(created);
  }

  /** Ids of every active, due template across all users — for the scheduled per-template sweep. */
  @Transactional(readOnly = true)
  public List<Long> dueTemplateIds() {
    return recurringRepository
        .findByActiveTrueAndNextRunDateLessThanEqual(LocalDate.now(SCHEDULE_ZONE))
        .stream()
        .map(RecurringTransaction::getId)
        .toList();
  }

  /**
   * Materialize a single template in its own transaction — the sweep calls this per id so one
   * template's optimistic-lock conflict rolls back only that template, not the whole night's run.
   * Returns how many transactions it created (0 if the template vanished or is no longer active).
   */
  @Transactional
  public int materializeTemplate(long templateId) {
    RecurringTransaction template = recurringRepository.findById(templateId).orElse(null);
    if (template == null || !template.isActive()) {
      return 0;
    }
    return materializeOne(template, LocalDate.now(SCHEDULE_ZONE));
  }

  /**
   * Advance one template through its due occurrences (catching up past ones, capped) creating a
   * transaction per occurrence — skipping any whose dedupe hash already exists (a backstop
   * consistent with import/restore, should a run and the sweep overlap). A completed template is
   * deactivated. Not {@code @Transactional} itself — the caller owns the boundary (per-template for
   * the sweep, per-request for {@code /run}). The rate is resolved per template owner (cross-user).
   */
  private int materializeOne(RecurringTransaction template, LocalDate today) {
    BigDecimal rate = rateResolver.resolve(template.getUserId(), template.getCurrency(), null);
    Set<String> hashes =
        new HashSet<>(
            transactionRepository.findDedupeHashesByUserIdAndAccountId(
                template.getUserId(), template.getAccountId()));
    List<Transaction> toInsert = new ArrayList<>();
    int guard = 0;
    while (!template.getNextRunDate().isAfter(today)
        && (template.getEndDate() == null
            || !template.getNextRunDate().isAfter(template.getEndDate()))
        && guard < MAX_MATERIALIZE_PER_RUN) {
      Transaction tx = newTransaction(template, template.getNextRunDate(), rate);
      if (hashes.add(tx.getDedupeHash())) { // skip an occurrence already present (dedupe backstop)
        toInsert.add(tx);
      }
      template.setNextRunDate(
          template.getFrequency().advance(template.getNextRunDate(), template.getIntervalCount()));
      guard++;
    }
    if (template.getEndDate() != null && template.getNextRunDate().isAfter(template.getEndDate())) {
      template.setActive(false);
    }
    transactionRepository.saveAll(toInsert);
    recurringRepository.save(template);
    return toInsert.size();
  }

  private Transaction newTransaction(
      RecurringTransaction template, LocalDate date, BigDecimal rate) {
    Transaction tx = new Transaction();
    tx.setUserId(template.getUserId());
    tx.setDate(date);
    tx.setAmountMinor(template.getAmountMinor());
    tx.setType(template.getType());
    tx.setAccountId(template.getAccountId());
    tx.setCounterAccountId(null);
    tx.setCategoryId(template.getCategoryId());
    tx.setCurrency(template.getCurrency());
    tx.setRateToBase(rate);
    tx.setDescription(template.getDescription());
    tx.setNote(template.getNote());
    tx.setRecurringId(template.getId());
    tx.setDedupeHash(
        DedupeHash.of(
            List.of(
                date.toString(),
                template.getAmountMinor(),
                template.getCurrency(),
                template.getAccountId(),
                template.getDescription())));
    return tx;
  }

  private RecurringTransaction requireOwned(long userId, long id) {
    return recurringRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> NotFoundException.of("RecurringTransaction", id));
  }

  private Long validateCategory(long userId, TransactionType type, Long categoryId) {
    if (categoryId == null) {
      return null;
    }
    Category category =
        categoryRepository
            .findByIdAndUserId(categoryId, userId)
            .orElseThrow(() -> NotFoundException.of("Category", categoryId));
    CategoryKind required =
        type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
    if (category.getKind() != required) {
      throw new UnprocessableEntityException("Category kind does not match the recurring type.");
    }
    return category.getId();
  }

  private static void requireCurrentVersion(RecurringTransaction template, long expectedVersion) {
    if (template.getVersion() == null || template.getVersion() != expectedVersion) {
      throw new ConflictException(
          "Recurring template was modified concurrently; reload and retry.");
    }
  }

  private static RecurringResponse toResponse(RecurringTransaction r) {
    return new RecurringResponse(
        r.getId(),
        r.getAccountId(),
        r.getCategoryId(),
        r.getAmountMinor(),
        r.getType(),
        r.getCurrency(),
        r.getDescription(),
        r.getNote(),
        r.getFrequency(),
        r.getIntervalCount(),
        r.getStartDate(),
        r.getEndDate(),
        r.getNextRunDate(),
        r.isActive(),
        r.getVersion());
  }
}
