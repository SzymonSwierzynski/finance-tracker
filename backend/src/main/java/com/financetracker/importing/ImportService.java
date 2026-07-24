package com.financetracker.importing;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryKind;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.error.NotFoundException;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.common.hash.DedupeHash;
import com.financetracker.fx.RateResolver;
import com.financetracker.importing.CsvParser.ParsedCsv;
import com.financetracker.importing.dto.CommitResponse;
import com.financetracker.importing.dto.ImportBatchResponse;
import com.financetracker.importing.dto.ImportMapping;
import com.financetracker.importing.dto.PreviewResponse;
import com.financetracker.importing.dto.PreviewRow;
import com.financetracker.rule.RuleMatcher;
import com.financetracker.rule.RuleMatcher.MatchableRule;
import com.financetracker.rule.RuleRepository;
import com.financetracker.transaction.Transaction;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV import pipeline (preview → commit), ported from the prototype's {@code data/import.ts}. The
 * backend owns parsing, decoding and dedupe so behaviour is identical across clients (CLAUDE.md
 * §8). Commit locks the account's rate to base, auto-categorizes via the rules engine (respecting
 * the category/type kind invariant), deduplicates against existing rows and within the file, and
 * groups the result as one undoable batch.
 */
@Service
public class ImportService {

  /** Cap on data rows per import (CLAUDE.md §5: bound upload size and row count). */
  private static final int MAX_ROWS = 10_000;

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final ImportBatchRepository importBatchRepository;
  private final ImportProfileRepository importProfileRepository;
  private final RuleRepository ruleRepository;
  private final CategoryRepository categoryRepository;
  private final RateResolver rateResolver;

  public ImportService(
      AccountRepository accountRepository,
      TransactionRepository transactionRepository,
      ImportBatchRepository importBatchRepository,
      ImportProfileRepository importProfileRepository,
      RuleRepository ruleRepository,
      CategoryRepository categoryRepository,
      RateResolver rateResolver) {
    this.accountRepository = accountRepository;
    this.transactionRepository = transactionRepository;
    this.importBatchRepository = importBatchRepository;
    this.importProfileRepository = importProfileRepository;
    this.ruleRepository = ruleRepository;
    this.categoryRepository = categoryRepository;
    this.rateResolver = rateResolver;
  }

  @Transactional(readOnly = true)
  public PreviewResponse preview(long userId, long accountId, byte[] file, ImportMapping mapping) {
    Account account = requireOwnedAccount(userId, accountId);
    String text = CsvDecoder.decode(file, mapping.encoding());
    boolean misdecoded = CsvDecoder.looksMisdecoded(text);
    ParsedCsv parsed = CsvParser.parse(text, mapping.delimiter());
    List<ParsedImportRow> rows =
        requireWithinRowLimit(ImportRowBuilder.build(parsed.rows(), mapping));

    Set<String> existing =
        new HashSet<>(
            transactionRepository.findDedupeHashesByUserIdAndAccountId(userId, accountId));
    Set<String> seenInFile = new HashSet<>();

    List<PreviewRow> previewRows = new ArrayList<>();
    int validRows = 0;
    int duplicateRows = 0;
    for (ParsedImportRow row : rows) {
      boolean duplicate = false;
      if (row.valid()) {
        validRows++;
        String hash = dedupeHash(row, account.getCurrency(), accountId);
        duplicate = existing.contains(hash) || !seenInFile.add(hash);
        if (duplicate) {
          duplicateRows++;
        }
      }
      previewRows.add(
          new PreviewRow(
              row.index(),
              row.date(),
              row.amountMinor(),
              row.type(),
              row.description(),
              row.valid(),
              row.error(),
              duplicate));
    }
    return new PreviewResponse(
        String.valueOf(parsed.delimiter()),
        misdecoded,
        rows.size(),
        validRows,
        duplicateRows,
        previewRows);
  }

  @Transactional
  public CommitResponse commit(
      long userId, long accountId, String fileName, byte[] file, ImportMapping mapping) {
    Account account = requireOwnedAccount(userId, accountId);
    String currency = account.getCurrency();
    // Lock the rate to base once for the whole import (422 if the currency has no usable rate).
    BigDecimal rateToBase = rateResolver.resolve(userId, currency, null);

    ParsedCsv parsed =
        CsvParser.parse(CsvDecoder.decode(file, mapping.encoding()), mapping.delimiter());
    List<ParsedImportRow> rows =
        requireWithinRowLimit(ImportRowBuilder.build(parsed.rows(), mapping));

    List<MatchableRule> rules =
        ruleRepository.findByUserIdOrderByPriorityDescPatternAsc(userId).stream()
            .map(r -> new MatchableRule(r.getPattern(), r.getCategoryId(), r.getPriority()))
            .toList();
    Map<Long, CategoryKind> kindById = new HashMap<>();
    for (Category category : categoryRepository.findByUserIdOrderByNameAsc(userId)) {
      kindById.put(category.getId(), category.getKind());
    }
    // Dedupe against existing rows for this account and within the file (seed then add-as-we-go).
    Set<String> seen =
        new HashSet<>(
            transactionRepository.findDedupeHashesByUserIdAndAccountId(userId, accountId));

    List<Transaction> toInsert = new ArrayList<>();
    int skippedDuplicates = 0;
    int skippedInvalid = 0;
    for (ParsedImportRow row : rows) {
      if (!row.valid() || row.date() == null || row.amountMinor() == null) {
        skippedInvalid++;
        continue;
      }
      String hash = dedupeHash(row, currency, accountId);
      if (!seen.add(hash)) {
        skippedDuplicates++;
        continue;
      }
      toInsert.add(
          newTransaction(userId, accountId, currency, rateToBase, row, hash, rules, kindById));
    }

    if (toInsert.isEmpty()) {
      // No batch for an all-duplicate/all-invalid file, but still remember the mapping.
      upsertProfile(userId, accountId, mapping);
      return new CommitResponse(null, 0, skippedDuplicates, skippedInvalid);
    }

    ImportBatch batch = new ImportBatch();
    batch.setUserId(userId);
    batch.setAccountId(accountId);
    batch.setFileName(fileName);
    batch.setCount(toInsert.size());
    Long batchId = importBatchRepository.save(batch).getId();
    for (Transaction tx : toInsert) {
      tx.setImportBatchId(batchId);
    }
    transactionRepository.saveAll(toInsert);
    upsertProfile(userId, accountId, mapping);
    return new CommitResponse(batchId, toInsert.size(), skippedDuplicates, skippedInvalid);
  }

  @Transactional(readOnly = true)
  public List<ImportBatchResponse> listBatches(long userId) {
    return importBatchRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
        .map(
            b ->
                new ImportBatchResponse(
                    b.getId(), b.getAccountId(), b.getFileName(), b.getCount(), b.getCreatedAt()))
        .toList();
  }

  /**
   * Undo an import: delete the batch (its transactions cascade away). Returns the count removed.
   */
  @Transactional
  public int undoBatch(long userId, long batchId) {
    ImportBatch batch =
        importBatchRepository
            .findByIdAndUserId(batchId, userId)
            .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
    int count = batch.getCount();
    importBatchRepository.delete(batch);
    return count;
  }

  @Transactional(readOnly = true)
  public ImportMapping getProfile(long userId, long accountId) {
    requireOwnedAccount(userId, accountId);
    return importProfileRepository
        .findByUserIdAndAccountId(userId, accountId)
        .map(ImportService::toMapping)
        .orElseThrow(() -> NotFoundException.of("ImportProfile", accountId));
  }

  @Transactional
  public ImportMapping saveProfile(long userId, long accountId, ImportMapping mapping) {
    requireOwnedAccount(userId, accountId);
    return toMapping(upsertProfile(userId, accountId, mapping));
  }

  private Transaction newTransaction(
      long userId,
      long accountId,
      String currency,
      BigDecimal rateToBase,
      ParsedImportRow row,
      String hash,
      List<MatchableRule> rules,
      Map<Long, CategoryKind> kindById) {
    Long categoryId = null;
    Long matched = RuleMatcher.match(row.description(), rules);
    if (matched != null && kindMatches(kindById.get(matched), row.type())) {
      categoryId = matched;
    }
    Transaction tx = new Transaction();
    tx.setUserId(userId);
    tx.setDate(row.date());
    tx.setAmountMinor(row.amountMinor());
    tx.setType(row.type());
    tx.setAccountId(accountId);
    tx.setCounterAccountId(null);
    tx.setCategoryId(categoryId);
    tx.setCurrency(currency);
    tx.setRateToBase(rateToBase);
    tx.setDescription(row.description());
    tx.setNote("");
    tx.setDedupeHash(hash);
    return tx;
  }

  private ImportProfile upsertProfile(long userId, long accountId, ImportMapping mapping) {
    ImportProfile profile =
        importProfileRepository
            .findByUserIdAndAccountId(userId, accountId)
            .orElseGet(
                () -> {
                  ImportProfile fresh = new ImportProfile();
                  fresh.setUserId(userId);
                  fresh.setAccountId(accountId);
                  return fresh;
                });
    profile.setDelimiter(mapping.delimiter());
    profile.setEncoding(mapping.encoding());
    profile.setHasHeader(mapping.hasHeader());
    profile.setDateIndex(mapping.dateIndex());
    profile.setDateFormat(mapping.dateFormat() == null ? "auto" : mapping.dateFormat());
    profile.setDescriptionIndex(mapping.descriptionIndex());
    profile.setAmountMode(mapping.amountMode());
    profile.setAmountIndex(mapping.amountIndex());
    profile.setExpenseIsNegative(mapping.expenseIsNegative());
    profile.setDebitIndex(mapping.debitIndex());
    profile.setCreditIndex(mapping.creditIndex());
    return importProfileRepository.save(profile);
  }

  private Account requireOwnedAccount(long userId, long accountId) {
    return accountRepository
        .findByIdAndUserId(accountId, userId)
        .orElseThrow(() -> NotFoundException.of("Account", accountId));
  }

  private static List<ParsedImportRow> requireWithinRowLimit(List<ParsedImportRow> rows) {
    if (rows.size() > MAX_ROWS) {
      throw new UnprocessableEntityException(
          "CSV has more than " + MAX_ROWS + " rows; split the file and import in parts.");
    }
    return rows;
  }

  private static boolean kindMatches(CategoryKind kind, TransactionType type) {
    if (kind == null) {
      return false;
    }
    return type == TransactionType.INCOME
        ? kind == CategoryKind.INCOME
        : kind == CategoryKind.EXPENSE;
  }

  private static String dedupeHash(ParsedImportRow row, String currency, long accountId) {
    return DedupeHash.of(
        List.of(row.date().toString(), row.amountMinor(), currency, accountId, row.description()));
  }

  private static ImportMapping toMapping(ImportProfile p) {
    return new ImportMapping(
        p.getDelimiter(),
        p.getEncoding(),
        p.isHasHeader(),
        p.getDateIndex(),
        p.getDateFormat(),
        p.getDescriptionIndex(),
        p.getAmountMode(),
        p.getAmountIndex(),
        p.isExpenseIsNegative(),
        p.getDebitIndex(),
        p.getCreditIndex());
  }
}
