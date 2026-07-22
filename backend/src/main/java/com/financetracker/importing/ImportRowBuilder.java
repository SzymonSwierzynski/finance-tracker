package com.financetracker.importing;

import com.financetracker.common.money.MoneyUtil;
import com.financetracker.importing.dto.ImportMapping;
import com.financetracker.transaction.TransactionType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Interpret raw CSV rows against a column mapping — a faithful port of the prototype's {@code
 * buildImportRows}. Pure: one entry per data row (header skipped), each flagged valid/invalid so
 * the UI can preview and let the user fix the mapping before committing. Amount magnitude and sign
 * use {@link MoneyUtil#parseAmountToMinor} (decimal comma, space/NBSP thousands, EU/US separators).
 */
public final class ImportRowBuilder {

  private ImportRowBuilder() {}

  public static List<ParsedImportRow> build(List<List<String>> rows, ImportMapping mapping) {
    List<ParsedImportRow> out = new ArrayList<>();
    int startAt = mapping.hasHeader() ? 1 : 0;

    for (int i = startAt; i < rows.size(); i++) {
      List<String> raw = rows.get(i);
      String description = cell(raw, mapping.descriptionIndex());
      LocalDate date =
          CsvDateParser.parseFlexible(cell(raw, mapping.dateIndex()), mapping.dateFormat());

      Long amountMinor = null;
      TransactionType type = TransactionType.EXPENSE;

      if (mapping.amountMode() == AmountMode.SIGNED) {
        Long signed = MoneyUtil.parseAmountToMinor(cell(raw, mapping.amountIndex()));
        if (signed != null && signed != 0) {
          boolean negative = signed < 0;
          boolean isExpense = mapping.expenseIsNegative() ? negative : !negative;
          type = isExpense ? TransactionType.EXPENSE : TransactionType.INCOME;
          amountMinor = Math.abs(signed);
        }
      } else {
        long debit = magnitude(cell(raw, mapping.debitIndex()));
        long credit = magnitude(cell(raw, mapping.creditIndex()));
        if (debit > 0) {
          type = TransactionType.EXPENSE;
          amountMinor = debit;
        } else if (credit > 0) {
          type = TransactionType.INCOME;
          amountMinor = credit;
        }
      }

      List<String> problems = new ArrayList<>();
      if (date == null) {
        problems.add("date");
      }
      if (amountMinor == null || amountMinor <= 0) {
        problems.add("amount");
      }
      boolean valid = problems.isEmpty();
      out.add(
          new ParsedImportRow(
              i,
              date,
              amountMinor,
              type,
              description,
              valid,
              valid ? null : "Invalid " + String.join(" & ", problems)));
    }

    return out;
  }

  private static String cell(List<String> row, int idx) {
    if (idx < 0 || idx >= row.size()) {
      return "";
    }
    String value = row.get(idx);
    return value == null ? "" : value.trim();
  }

  /** Parse a cell to a positive magnitude (0 when empty/invalid). */
  private static long magnitude(String value) {
    Long parsed = MoneyUtil.parseAmountToMinor(value);
    return parsed == null ? 0L : Math.abs(parsed);
  }
}
