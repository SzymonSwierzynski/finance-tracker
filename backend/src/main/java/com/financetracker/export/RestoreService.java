package com.financetracker.export;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.account.AccountType;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryKind;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.hash.DedupeHash;
import com.financetracker.export.dto.BackupResponse;
import com.financetracker.export.dto.BackupResponse.AccountBackup;
import com.financetracker.export.dto.BackupResponse.CategoryBackup;
import com.financetracker.export.dto.ExportedTransaction;
import com.financetracker.export.dto.RestoreSummary;
import com.financetracker.settings.SettingsService;
import com.financetracker.settings.dto.UpdateSettingsRequest;
import com.financetracker.transaction.Transaction;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Restores a {@link BackupResponse} (the inverse of {@link ExportService#backup}). The whole
 * restore runs in one transaction, so a malformed backup rolls back cleanly — nothing is
 * half-written.
 *
 * <p>Semantics are <b>additive and idempotent</b> (CLAUDE.md §10, lossless round-trip): accounts
 * and categories are matched by name — reused if the user already has one, otherwise created — and
 * transactions are deduped by the same hash CSV import uses, so restoring the same backup twice
 * adds nothing the second time. Money stays integer minor units and each transaction's locked
 * {@code rateToBase} is preserved, so a report over restored data reads identically.
 *
 * <p>Transfers carry their counter-account by name and are restored too; a transfer whose
 * counter-account can't be resolved (e.g. corrupt input) is skipped and counted in the summary.
 */
@Service
public class RestoreService {

  private static final String DEFAULT_CATEGORY_COLOR = "#94a3b8";

  private final AccountRepository accountRepository;
  private final CategoryRepository categoryRepository;
  private final TransactionRepository transactionRepository;
  private final SettingsService settingsService;

  public RestoreService(
      AccountRepository accountRepository,
      CategoryRepository categoryRepository,
      TransactionRepository transactionRepository,
      SettingsService settingsService) {
    this.accountRepository = accountRepository;
    this.categoryRepository = categoryRepository;
    this.transactionRepository = transactionRepository;
    this.settingsService = settingsService;
  }

  @Transactional
  public RestoreSummary restore(long userId, BackupResponse backup) {
    restoreReportingCurrency(userId, backup.reportingCurrency());

    Map<String, Long> accountIdByName = new HashMap<>();
    int accountsCreated = restoreAccounts(userId, backup.accounts(), accountIdByName);

    Map<String, Long> categoryIdByName = new HashMap<>();
    int categoriesCreated = restoreCategories(userId, backup.categories(), categoryIdByName);

    return restoreTransactions(
        userId,
        backup.transactions(),
        accountIdByName,
        categoryIdByName,
        accountsCreated,
        categoriesCreated);
  }

  private void restoreReportingCurrency(long userId, String currency) {
    // Only a well-formed ISO code is applied; anything else leaves the current setting untouched.
    if (currency != null && currency.matches("^[A-Za-z]{3}$")) {
      settingsService.update(userId, new UpdateSettingsRequest(currency));
    }
  }

  /**
   * Find-or-create every backup account by name, populating {@code idByName} (name → id) for
   * transaction resolution. Returns how many accounts were created.
   */
  private int restoreAccounts(
      long userId, List<AccountBackup> accounts, Map<String, Long> idByName) {
    for (Account existing : accountRepository.findByUserIdOrderByNameAsc(userId)) {
      idByName.put(existing.getName(), existing.getId());
    }
    int preExisting = idByName.size();
    for (AccountBackup ab : nullSafe(accounts)) {
      if (ab.name() == null || idByName.containsKey(ab.name())) {
        continue;
      }
      Account account = new Account();
      account.setUserId(userId);
      account.setName(ab.name());
      account.setType(AccountType.fromValue(ab.type()));
      account.setCurrency(ab.currency());
      account.setStartingBalanceMinor(ab.startingBalanceMinor());
      account.setTrackBalance(ab.trackBalance());
      account.setArchived(ab.archived());
      idByName.put(account.getName(), accountRepository.save(account).getId());
    }
    return idByName.size() - preExisting;
  }

  /**
   * Find-or-create categories (parents before children so a child's parent resolves), populating
   * {@code idByName} (name → id) for transaction resolution. Returns how many were created.
   */
  private int restoreCategories(
      long userId, List<CategoryBackup> categories, Map<String, Long> idByName) {
    // Existing categories keyed by (parentId, lower-name) for find-or-create, plus a lower-name →
    // id index over top-level ones so a child's parent name resolves to its id.
    Map<String, Category> existingByKey = new HashMap<>();
    Map<String, Long> topLevelIdByName = new HashMap<>();
    for (Category c : categoryRepository.findByUserIdOrderByNameAsc(userId)) {
      idByName.put(c.getName(), c.getId());
      existingByKey.put(childKey(c.getParentId(), c.getName()), c);
      if (c.getParentId() == null) {
        topLevelIdByName.put(c.getName().toLowerCase(Locale.ROOT), c.getId());
      }
    }

    int created = 0;
    for (CategoryBackup cb : nullSafe(categories)) { // parents first
      if (cb.name() != null && cb.parent() == null) {
        boolean isNew = !existingByKey.containsKey(childKey(null, cb.name()));
        Long id = findOrCreateCategory(userId, cb, null, existingByKey);
        idByName.put(cb.name(), id);
        topLevelIdByName.put(cb.name().toLowerCase(Locale.ROOT), id);
        if (isNew) {
          created++;
        }
      }
    }
    for (CategoryBackup cb : nullSafe(categories)) { // then children
      if (cb.name() != null && cb.parent() != null) {
        Long parentId = topLevelIdByName.get(cb.parent().toLowerCase(Locale.ROOT));
        if (parentId == null) {
          continue; // parent absent from the backup (corrupt input) — skip rather than misplace
        }
        boolean isNew = !existingByKey.containsKey(childKey(parentId, cb.name()));
        idByName.put(cb.name(), findOrCreateCategory(userId, cb, parentId, existingByKey));
        if (isNew) {
          created++;
        }
      }
    }
    return created;
  }

  private Long findOrCreateCategory(
      long userId, CategoryBackup cb, Long parentId, Map<String, Category> existingByKey) {
    Category existing = existingByKey.get(childKey(parentId, cb.name()));
    if (existing != null) {
      return existing.getId();
    }
    Category category = new Category();
    category.setUserId(userId);
    category.setName(cb.name());
    category.setKind(CategoryKind.fromValue(cb.kind()));
    category.setParentId(parentId);
    category.setColor(cb.color() == null ? DEFAULT_CATEGORY_COLOR : cb.color());
    Category saved = categoryRepository.save(category);
    existingByKey.put(childKey(parentId, cb.name()), saved);
    return saved.getId();
  }

  private RestoreSummary restoreTransactions(
      long userId,
      List<ExportedTransaction> transactions,
      Map<String, Long> accountIdByName,
      Map<String, Long> categoryIdByName,
      int accountsCreated,
      int categoriesCreated) {
    Map<Long, Set<String>> hashesByAccount = new HashMap<>();
    List<Transaction> toInsert = new ArrayList<>();
    int imported = 0;
    int skipped = 0;
    int transfersSkipped = 0;
    for (ExportedTransaction t : nullSafe(transactions)) {
      Long accountId = accountIdByName.get(t.account());
      if (accountId == null) {
        skipped++; // account name didn't resolve (shouldn't happen once accounts are restored)
        continue;
      }
      Long counterAccountId = null;
      if (TransactionType.TRANSFER.value().equals(t.type())) {
        counterAccountId =
            StringUtils.hasText(t.counterAccount())
                ? accountIdByName.get(t.counterAccount())
                : null;
        if (counterAccountId == null) {
          transfersSkipped++; // a transfer can't be rebuilt without its other side
          continue;
        }
      }
      Set<String> seen =
          hashesByAccount.computeIfAbsent(
              accountId,
              id ->
                  new HashSet<>(
                      transactionRepository.findDedupeHashesByUserIdAndAccountId(userId, id)));
      String hash =
          DedupeHash.of(
              List.of(t.date(), t.amountMinor(), t.currency(), accountId, t.description()));
      if (!seen.add(hash)) {
        skipped++; // already present, or a duplicate within this backup
        continue;
      }
      toInsert.add(newTransaction(userId, t, accountId, counterAccountId, categoryIdByName, hash));
      imported++;
    }
    transactionRepository.saveAll(toInsert);
    return new RestoreSummary(
        accountsCreated, categoriesCreated, imported, skipped, transfersSkipped);
  }

  private Transaction newTransaction(
      long userId,
      ExportedTransaction t,
      long accountId,
      Long counterAccountId,
      Map<String, Long> categoryIdByName,
      String dedupeHash) {
    Transaction tx = new Transaction();
    tx.setUserId(userId);
    tx.setDate(LocalDate.parse(t.date()));
    tx.setAmountMinor(t.amountMinor());
    TransactionType type = TransactionType.fromValue(t.type());
    tx.setType(type);
    tx.setAccountId(accountId);
    tx.setCounterAccountId(counterAccountId);
    // Transfers are never categorized (CLAUDE.md §6); otherwise resolve the category by name.
    tx.setCategoryId(
        type == TransactionType.TRANSFER || !StringUtils.hasText(t.category())
            ? null
            : categoryIdByName.get(t.category()));
    tx.setCurrency(t.currency());
    tx.setRateToBase(t.rateToBase()); // preserve the locked rate (CLAUDE.md §7)
    tx.setDescription(t.description() == null ? "" : t.description());
    tx.setNote(t.note() == null ? "" : t.note());
    tx.setDedupeHash(dedupeHash);
    return tx;
  }

  private static String childKey(Long parentId, String name) {
    return parentId + " " + name.toLowerCase(Locale.ROOT);
  }

  private static <T> List<T> nullSafe(List<T> list) {
    return list == null ? List.of() : list;
  }
}
