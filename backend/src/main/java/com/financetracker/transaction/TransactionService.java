package com.financetracker.transaction;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.common.error.ConflictException;
import com.financetracker.common.error.NotFoundException;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.common.hash.DedupeHash;
import com.financetracker.common.money.MoneyUtil;
import com.financetracker.common.web.PageResponse;
import com.financetracker.fx.RateResolver;
import com.financetracker.transaction.dto.CreateTransactionRequest;
import com.financetracker.transaction.dto.TransactionResponse;
import com.financetracker.transaction.dto.UpdateTransactionRequest;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Transaction business logic: ownership of the account(s) it touches, transfer/category invariants,
 * and — critically — resolving and LOCKING the rate to base at entry (CLAUDE.md §7). The dedupe
 * hash is computed for manual entries too so imports (Phase 4) dedupe consistently.
 */
@Service
public class TransactionService {

  private static final int MAX_PAGE_SIZE = 200;

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final RateResolver rateResolver;

  public TransactionService(
      TransactionRepository transactionRepository,
      AccountRepository accountRepository,
      RateResolver rateResolver) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.rateResolver = rateResolver;
  }

  @Transactional
  public TransactionResponse create(long userId, CreateTransactionRequest request) {
    Account account = requireOwnedAccount(userId, request.accountId());

    // Currency defaults to the account's; an explicit code is upper-cased.
    String currency =
        StringUtils.hasText(request.currency())
            ? request.currency().toUpperCase(Locale.ROOT)
            : account.getCurrency();

    BigDecimal rateToBase = rateResolver.resolve(userId, currency, request.rateToBase());
    String description = request.description() == null ? "" : request.description().trim();
    String note = request.note() == null ? "" : request.note().trim();
    boolean isTransfer = request.type() == TransactionType.TRANSFER;

    Long counterAccountId = null;
    if (isTransfer) {
      if (request.counterAccountId() == null) {
        throw new UnprocessableEntityException("A transfer requires a counterAccountId.");
      }
      if (request.counterAccountId().equals(request.accountId())) {
        throw new UnprocessableEntityException(
            "A transfer's counter account must differ from its account.");
      }
      // Validate ownership of the counter account (404 — don't leak existence).
      requireOwnedAccount(userId, request.counterAccountId());
      counterAccountId = request.counterAccountId();
    }

    Transaction tx = new Transaction();
    tx.setUserId(userId);
    tx.setDate(request.date());
    tx.setAmountMinor(request.amountMinor());
    tx.setType(request.type());
    tx.setAccountId(request.accountId());
    tx.setCounterAccountId(counterAccountId);
    tx.setCategoryId(null); // categories arrive in Phase 3
    tx.setCurrency(currency);
    tx.setRateToBase(rateToBase);
    tx.setDescription(description);
    tx.setNote(note);
    tx.setDedupeHash(
        dedupeHash(
            request.date(), request.amountMinor(), currency, request.accountId(), description));

    return toResponse(transactionRepository.save(tx));
  }

  @Transactional(readOnly = true)
  public PageResponse<TransactionResponse> list(
      long userId,
      LocalDate from,
      LocalDate to,
      Long accountId,
      TransactionType type,
      int page,
      int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int safePage = Math.max(page, 0);
    // Newest first; id as a stable tie-break (matches the prototype).
    Pageable pageable =
        PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));
    Page<Transaction> result =
        transactionRepository.findAll(filter(userId, from, to, accountId, type), pageable);
    List<TransactionResponse> items = result.getContent().stream().map(this::toResponse).toList();
    return PageResponse.of(items, result);
  }

  /**
   * Dynamic, user-scoped filter. Null filters are skipped; the account filter matches either side
   * of a transfer (account or counter account), mirroring the prototype.
   */
  private static Specification<Transaction> filter(
      long userId, LocalDate from, LocalDate to, Long accountId, TransactionType type) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userId"), userId));
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("date"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("date"), to));
      }
      if (accountId != null) {
        predicates.add(
            cb.or(
                cb.equal(root.get("accountId"), accountId),
                cb.equal(root.get("counterAccountId"), accountId)));
      }
      if (type != null) {
        predicates.add(cb.equal(root.get("type"), type));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @Transactional(readOnly = true)
  public TransactionResponse get(long userId, long id) {
    return toResponse(requireOwned(userId, id));
  }

  @Transactional
  public TransactionResponse update(long userId, long id, UpdateTransactionRequest request) {
    Transaction tx = requireOwned(userId, id);
    requireCurrentVersion(tx, request.version());

    if (request.date() != null) {
      tx.setDate(request.date());
    }
    if (request.amountMinor() != null) {
      tx.setAmountMinor(request.amountMinor());
    }
    if (request.description() != null) {
      tx.setDescription(request.description().trim());
    }
    if (request.note() != null) {
      tx.setNote(request.note().trim());
    }
    // Inputs to the dedupe hash may have changed; keep it in sync.
    tx.setDedupeHash(
        dedupeHash(
            tx.getDate(),
            tx.getAmountMinor(),
            tx.getCurrency(),
            tx.getAccountId(),
            tx.getDescription()));

    // Flush so the response carries the incremented optimistic-lock version.
    return toResponse(transactionRepository.saveAndFlush(tx));
  }

  @Transactional
  public void delete(long userId, long id) {
    Transaction tx = requireOwned(userId, id);
    transactionRepository.delete(tx);
  }

  private Account requireOwnedAccount(long userId, long accountId) {
    return accountRepository
        .findByIdAndUserId(accountId, userId)
        .orElseThrow(() -> NotFoundException.of("Account", accountId));
  }

  private Transaction requireOwned(long userId, long id) {
    return transactionRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> NotFoundException.of("Transaction", id));
  }

  private static void requireCurrentVersion(Transaction tx, long expectedVersion) {
    if (tx.getVersion() == null || tx.getVersion() != expectedVersion) {
      throw new ConflictException("Transaction was modified concurrently; reload and retry.");
    }
  }

  private static String dedupeHash(
      LocalDate date, long amountMinor, String currency, long accountId, String description) {
    return DedupeHash.of(List.of(date.toString(), amountMinor, currency, accountId, description));
  }

  private TransactionResponse toResponse(Transaction t) {
    return new TransactionResponse(
        t.getId(),
        t.getDate(),
        t.getAmountMinor(),
        t.getType(),
        t.getAccountId(),
        t.getCounterAccountId(),
        t.getCategoryId(),
        t.getCurrency(),
        t.getRateToBase(),
        MoneyUtil.toBaseMinor(t.getAmountMinor(), t.getRateToBase()),
        t.getDescription(),
        t.getNote(),
        t.getImportBatchId(),
        t.getDedupeHash(),
        t.getVersion());
  }
}
