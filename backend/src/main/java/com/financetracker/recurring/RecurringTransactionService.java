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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    LocalDate today = LocalDate.now();
    return new RunRecurringResponse(
        materialize(
            recurringRepository.findByUserIdAndActiveTrueAndNextRunDateLessThanEqual(userId, today),
            today));
  }

  /** Materialize every active, due template across all users — driven by the scheduled sweep. */
  @Transactional
  public int materializeAllDue() {
    LocalDate today = LocalDate.now();
    return materialize(
        recurringRepository.findByActiveTrueAndNextRunDateLessThanEqual(today), today);
  }

  /**
   * Advance each template through its due occurrences (catching up past ones, capped) until it
   * passes today or its end date, creating a transaction per occurrence; a completed template is
   * deactivated. The rate is resolved per template's owner so this works cross-user.
   */
  private int materialize(List<RecurringTransaction> due, LocalDate today) {
    List<Transaction> toInsert = new ArrayList<>();
    for (RecurringTransaction template : due) {
      BigDecimal rate = rateResolver.resolve(template.getUserId(), template.getCurrency(), null);
      int guard = 0;
      while (!template.getNextRunDate().isAfter(today)
          && (template.getEndDate() == null
              || !template.getNextRunDate().isAfter(template.getEndDate()))
          && guard < MAX_MATERIALIZE_PER_RUN) {
        toInsert.add(newTransaction(template, template.getNextRunDate(), rate));
        template.setNextRunDate(
            template
                .getFrequency()
                .advance(template.getNextRunDate(), template.getIntervalCount()));
        guard++;
      }
      if (template.getEndDate() != null
          && template.getNextRunDate().isAfter(template.getEndDate())) {
        template.setActive(false);
      }
    }
    transactionRepository.saveAll(toInsert);
    recurringRepository.saveAll(due);
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
