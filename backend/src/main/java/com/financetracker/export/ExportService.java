package com.financetracker.export;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.export.dto.BackupResponse;
import com.financetracker.export.dto.BackupResponse.AccountBackup;
import com.financetracker.export.dto.BackupResponse.CategoryBackup;
import com.financetracker.export.dto.ExportedTransaction;
import com.financetracker.settings.SettingsService;
import com.financetracker.transaction.TransactionRepository;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User data export (Phase 6). Transactions as JSON rows or CSV; money stays integer minor units so
 * an export round-trips losslessly. Account/category are resolved to their names for readability.
 */
@Service
public class ExportService {

  private static final String[] CSV_HEADERS = {
    "date",
    "type",
    "amountMinor",
    "currency",
    "rateToBase",
    "account",
    "counterAccount",
    "category",
    "categoryParent",
    "description",
    "note"
  };

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final CategoryRepository categoryRepository;
  private final SettingsService settingsService;

  public ExportService(
      TransactionRepository transactionRepository,
      AccountRepository accountRepository,
      CategoryRepository categoryRepository,
      SettingsService settingsService) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.categoryRepository = categoryRepository;
    this.settingsService = settingsService;
  }

  /**
   * A full snapshot of the user's data for backup: reporting currency, accounts, categories, and
   * every transaction. References are by name (not id) so a future restore can remap onto fresh
   * ids; money stays integer minor units and locked rates are preserved, so it round-trips.
   */
  @Transactional(readOnly = true)
  public BackupResponse backup(long userId) {
    List<AccountBackup> accounts =
        accountRepository.findByUserIdOrderByNameAsc(userId).stream()
            .map(
                a ->
                    new AccountBackup(
                        a.getName(),
                        a.getType().value(),
                        a.getCurrency(),
                        a.getStartingBalanceMinor(),
                        a.isTrackBalance(),
                        a.isArchived()))
            .toList();
    List<Category> cats = categoryRepository.findByUserIdOrderByNameAsc(userId);
    Map<Long, Category> byId = cats.stream().collect(Collectors.toMap(Category::getId, c -> c));
    List<CategoryBackup> categories =
        cats.stream()
            .map(
                c -> {
                  Category parent = c.getParentId() == null ? null : byId.get(c.getParentId());
                  return new CategoryBackup(
                      c.getName(),
                      c.getKind().value(),
                      parent == null ? null : parent.getName(),
                      c.getColor());
                })
            .toList();
    return new BackupResponse(
        settingsService.reportingCurrency(userId), accounts, categories, transactions(userId));
  }

  @Transactional(readOnly = true)
  public List<ExportedTransaction> transactions(long userId) {
    Map<Long, String> accountNames = new HashMap<>();
    for (Account account : accountRepository.findByUserIdOrderByNameAsc(userId)) {
      accountNames.put(account.getId(), account.getName());
    }
    Map<Long, Category> categoriesById = new HashMap<>();
    for (Category category : categoryRepository.findByUserIdOrderByNameAsc(userId)) {
      categoriesById.put(category.getId(), category);
    }
    return transactionRepository.findByUserIdOrderByDateAscIdAsc(userId).stream()
        .map(
            t -> {
              Category cat =
                  t.getCategoryId() == null ? null : categoriesById.get(t.getCategoryId());
              Category parent =
                  cat == null || cat.getParentId() == null
                      ? null
                      : categoriesById.get(cat.getParentId());
              return new ExportedTransaction(
                  t.getDate().toString(),
                  t.getType().value(),
                  t.getAmountMinor(),
                  t.getCurrency(),
                  t.getRateToBase(),
                  accountNames.getOrDefault(t.getAccountId(), ""),
                  t.getCounterAccountId() == null
                      ? ""
                      : accountNames.getOrDefault(t.getCounterAccountId(), ""),
                  cat == null ? "" : cat.getName(),
                  parent == null ? "" : parent.getName(),
                  t.getDescription(),
                  t.getNote());
            })
        .toList();
  }

  public String toCsv(List<ExportedTransaction> rows) {
    StringWriter out = new StringWriter();
    CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build();
    try (CSVPrinter printer = new CSVPrinter(out, format)) {
      for (ExportedTransaction r : rows) {
        // Neutralize spreadsheet formula injection on the free-text/name cells: description comes
        // from imported bank data (attacker-influenceable). Stored data stays raw — sanitize only
        // at the CSV edge.
        printer.printRecord(
            r.date(),
            r.type(),
            r.amountMinor(),
            r.currency(),
            r.rateToBase(),
            csvSafe(r.account()),
            csvSafe(r.counterAccount()),
            csvSafe(r.category()),
            csvSafe(r.categoryParent()),
            csvSafe(r.description()),
            csvSafe(r.note()));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write CSV export", e);
    }
    return out.toString();
  }

  /**
   * Prefix a leading formula trigger ({@code = + - @}, tab, CR) with a single quote so Excel /
   * Sheets / LibreOffice treat the cell as text, not a formula (CSV injection defence).
   */
  private static String csvSafe(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    return switch (value.charAt(0)) {
      case '=', '+', '-', '@', '\t', '\r' -> "'" + value;
      default -> value;
    };
  }
}
