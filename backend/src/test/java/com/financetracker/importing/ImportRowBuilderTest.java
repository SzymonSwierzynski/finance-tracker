package com.financetracker.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.importing.dto.ImportMapping;
import com.financetracker.transaction.TransactionType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Ported from the prototype's {@code data/importRows.test.ts}. */
class ImportRowBuilderTest {

  @Nested
  class SignedAmountColumn {

    // delimiter, encoding, hasHeader, headerRowIndex, dateIndex, dateFormat, descriptionIndex,
    // descriptionIndexes, amountMode, amountIndex, expenseIsNegative, debitIndex, creditIndex
    private final ImportMapping base =
        new ImportMapping(
            ";", "utf-8", true, null, 0, "auto", 1, null, AmountMode.SIGNED, 2, true, -1, -1);

    private final List<List<String>> rows =
        List.of(
            List.of("Date", "Title", "Amount"),
            List.of("15.05.2026", "Biedronka", "-19,99"),
            List.of("16.05.2026", "Salary", "5 000,00"),
            List.of("bad", "Broken", ""));

    @Test
    void skipsTheHeaderRow() {
      assertThat(ImportRowBuilder.build(rows, base)).hasSize(3);
    }

    @Test
    void readsANegativeAsAnExpenseWithPolishDecimalComma() {
      ParsedImportRow row = ImportRowBuilder.build(rows, base).get(0);
      assertThat(row.date()).isEqualTo(LocalDate.of(2026, 5, 15));
      assertThat(row.amountMinor()).isEqualTo(1999L);
      assertThat(row.type()).isEqualTo(TransactionType.EXPENSE);
      assertThat(row.valid()).isTrue();
    }

    @Test
    void readsAPositiveWithSpaceThousandsAsIncome() {
      ParsedImportRow row = ImportRowBuilder.build(rows, base).get(1);
      assertThat(row.date()).isEqualTo(LocalDate.of(2026, 5, 16));
      assertThat(row.amountMinor()).isEqualTo(500000L);
      assertThat(row.type()).isEqualTo(TransactionType.INCOME);
      assertThat(row.valid()).isTrue();
    }

    @Test
    void flagsRowsWithABadDateAndMissingAmount() {
      ParsedImportRow row = ImportRowBuilder.build(rows, base).get(2);
      assertThat(row.valid()).isFalse();
      assertThat(row.error()).contains("date").contains("amount");
    }

    @Test
    void honoursTheOppositeSignConvention() {
      ImportMapping flipped =
          new ImportMapping(
              ";", "utf-8", true, null, 0, "auto", 1, null, AmountMode.SIGNED, 2, false, -1, -1);
      List<ParsedImportRow> out = ImportRowBuilder.build(rows, flipped);
      assertThat(out.get(0).type())
          .isEqualTo(TransactionType.INCOME); // -19,99 now counts as income
      assertThat(out.get(1).type()).isEqualTo(TransactionType.EXPENSE);
    }
  }

  @Nested
  class SeparateDebitCreditColumns {

    private final ImportMapping mapping =
        new ImportMapping(
            ";", "utf-8", true, null, 0, "auto", 1, null, AmountMode.DEBIT_CREDIT, -1, true, 2, 3);

    private final List<List<String>> rows =
        List.of(
            List.of("Date", "Title", "Debit", "Credit"),
            List.of("15.05.2026", "Shop", "19,99", ""),
            List.of("16.05.2026", "Payroll", "", "5 000,00"));

    @Test
    void mapsTheDebitColumnToAnExpense() {
      ParsedImportRow row = ImportRowBuilder.build(rows, mapping).get(0);
      assertThat(row.amountMinor()).isEqualTo(1999L);
      assertThat(row.type()).isEqualTo(TransactionType.EXPENSE);
      assertThat(row.valid()).isTrue();
    }

    @Test
    void mapsTheCreditColumnToIncome() {
      ParsedImportRow row = ImportRowBuilder.build(rows, mapping).get(1);
      assertThat(row.amountMinor()).isEqualTo(500000L);
      assertThat(row.type()).isEqualTo(TransactionType.INCOME);
      assertThat(row.valid()).isTrue();
    }
  }
}
