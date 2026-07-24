package com.financetracker.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.importing.CsvParser.ParsedCsv;
import org.junit.jupiter.api.Test;

/**
 * Delimiter auto-detection and quoted-field handling — the parsing layer that lets Polish bank
 * exports (which favour {@code ';'} because {@code ','} is the decimal separator) import without
 * the user picking a delimiter. Ties in the sniff go to the first candidate ({@code ';'}).
 */
class CsvParserTest {

  @Test
  void usesAnExplicitDelimiterWhenGiven() {
    ParsedCsv parsed = CsvParser.parse("a;b;c\n1;2;3", ";");
    assertThat(parsed.delimiter()).isEqualTo(';');
    assertThat(parsed.rows()).hasSize(2);
    assertThat(parsed.rows().get(0)).containsExactly("a", "b", "c");
    assertThat(parsed.rows().get(1)).containsExactly("1", "2", "3");
  }

  @Test
  void autoDetectsSemicolonWhenCommasAreDecimalSeparators() {
    // The comma appears only in the amount on line 2, so the ';' header wins the first-line sniff.
    ParsedCsv parsed = CsvParser.parse("date;amount;desc\n2026-01-01;1 234,56;Shop", "");
    assertThat(parsed.delimiter()).isEqualTo(';');
    assertThat(parsed.rows().get(1)).containsExactly("2026-01-01", "1 234,56", "Shop");
  }

  @Test
  void autoDetectsComma() {
    assertThat(CsvParser.parse("a,b,c\n1,2,3", "").delimiter()).isEqualTo(',');
  }

  @Test
  void autoDetectsTab() {
    assertThat(CsvParser.parse("a\tb\tc\n1\t2\t3", "").delimiter()).isEqualTo('\t');
  }

  @Test
  void handlesQuotedFieldsWithEmbeddedDelimiter() {
    ParsedCsv parsed = CsvParser.parse("a;b\n\"x;y\";z", ";");
    assertThat(parsed.rows().get(1)).containsExactly("x;y", "z");
  }

  @Test
  void skipsBlankLinesAndSniffsTheFirstNonEmptyLine() {
    ParsedCsv parsed = CsvParser.parse("\n\nh1;h2\nv1;v2", "");
    assertThat(parsed.delimiter()).isEqualTo(';');
    assertThat(parsed.rows()).hasSize(2); // the two leading empty lines are ignored
  }

  @Test
  void emptyTextYieldsNoRows() {
    ParsedCsv parsed = CsvParser.parse("", "");
    assertThat(parsed.rows()).isEmpty();
    assertThat(parsed.delimiter()).isEqualTo(';'); // no separators found → first candidate wins
  }
}
