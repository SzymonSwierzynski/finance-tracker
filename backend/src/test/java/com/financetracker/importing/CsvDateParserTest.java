package com.financetracker.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Ported from the prototype's {@code lib/csvDate.test.ts}. */
class CsvDateParserTest {

  private static final LocalDate MAY_15 = LocalDate.of(2026, 5, 15);

  @Test
  void autoDetectsCommonPolishFormats() {
    assertThat(CsvDateParser.parseFlexible("2026-05-15", "auto")).isEqualTo(MAY_15);
    assertThat(CsvDateParser.parseFlexible("15.05.2026", "auto")).isEqualTo(MAY_15);
    assertThat(CsvDateParser.parseFlexible("15-05-2026", "auto")).isEqualTo(MAY_15);
  }

  @Test
  void usesAnExplicitFormatWhenGiven() {
    assertThat(CsvDateParser.parseFlexible("15/05/2026", "dd/MM/yyyy")).isEqualTo(MAY_15);
  }

  @Test
  void rejectsEmptiesAndGarbage() {
    assertThat(CsvDateParser.parseFlexible("", "auto")).isNull();
    assertThat(CsvDateParser.parseFlexible("   ", "auto")).isNull();
    assertThat(CsvDateParser.parseFlexible("not a date", "auto")).isNull();
  }

  @Test
  void rejectsOutOfRangeDatesViaTheRoundTripCheck() {
    assertThat(CsvDateParser.parseFlexible("2026-13-40", "auto")).isNull();
    assertThat(CsvDateParser.parseFlexible("32.01.2026", "dd.MM.yyyy")).isNull();
  }
}
