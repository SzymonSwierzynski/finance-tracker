package com.financetracker.export;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.export.dto.ExportedTransaction;
import com.financetracker.transaction.TransactionRepository;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    "category",
    "description",
    "note"
  };

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final CategoryRepository categoryRepository;

  public ExportService(
      TransactionRepository transactionRepository,
      AccountRepository accountRepository,
      CategoryRepository categoryRepository) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.categoryRepository = categoryRepository;
  }

  @Transactional(readOnly = true)
  public List<ExportedTransaction> transactions(long userId) {
    Map<Long, String> accountNames = new HashMap<>();
    for (Account account : accountRepository.findByUserIdOrderByNameAsc(userId)) {
      accountNames.put(account.getId(), account.getName());
    }
    Map<Long, String> categoryNames = new HashMap<>();
    for (Category category : categoryRepository.findByUserIdOrderByNameAsc(userId)) {
      categoryNames.put(category.getId(), category.getName());
    }
    return transactionRepository.findByUserIdOrderByDateAscIdAsc(userId).stream()
        .map(
            t ->
                new ExportedTransaction(
                    t.getDate().toString(),
                    t.getType().value(),
                    t.getAmountMinor(),
                    t.getCurrency(),
                    t.getRateToBase(),
                    accountNames.getOrDefault(t.getAccountId(), ""),
                    t.getCategoryId() == null
                        ? ""
                        : categoryNames.getOrDefault(t.getCategoryId(), ""),
                    t.getDescription(),
                    t.getNote()))
        .toList();
  }

  public String toCsv(List<ExportedTransaction> rows) {
    StringWriter out = new StringWriter();
    CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build();
    try (CSVPrinter printer = new CSVPrinter(out, format)) {
      for (ExportedTransaction r : rows) {
        printer.printRecord(
            r.date(),
            r.type(),
            r.amountMinor(),
            r.currency(),
            r.rateToBase(),
            r.account(),
            r.category(),
            r.description(),
            r.note());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write CSV export", e);
    }
    return out.toString();
  }
}
