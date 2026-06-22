package com.financetracker.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ported verbatim from the prototype's {@code money.test.ts} — these cases were validated against
 * real Polish bank data and are the contract for amount parsing.
 */
class MoneyUtilTest {

  @Nested
  class ParseAmountToMinor {

    @Test
    void parsesPlainDotDecimals() {
      assertThat(MoneyUtil.parseAmountToMinor("19.99")).isEqualTo(1999L);
      assertThat(MoneyUtil.parseAmountToMinor("0.01")).isEqualTo(1L);
      assertThat(MoneyUtil.parseAmountToMinor("1234")).isEqualTo(123400L);
      assertThat(MoneyUtil.parseAmountToMinor("5.")).isEqualTo(500L);
      assertThat(MoneyUtil.parseAmountToMinor(".5")).isEqualTo(50L);
    }

    @Test
    void parsesPolishDecimalComma() {
      assertThat(MoneyUtil.parseAmountToMinor("19,99")).isEqualTo(1999L);
      assertThat(MoneyUtil.parseAmountToMinor("0,01")).isEqualTo(1L);
      assertThat(MoneyUtil.parseAmountToMinor("1,5")).isEqualTo(150L);
    }

    @Test
    void parsesThousandsSeparatorsIncludingNbsp() {
      assertThat(MoneyUtil.parseAmountToMinor("1 234,56")).isEqualTo(123456L); // ASCII space
      // U+00A0 NBSP — Java's default \s would NOT strip this; the (?U) flag in MoneyUtil does.
      assertThat(MoneyUtil.parseAmountToMinor("1 234,56")).isEqualTo(123456L);
      // U+202F narrow NBSP, common in Polish bank exports.
      assertThat(MoneyUtil.parseAmountToMinor("1 234,56")).isEqualTo(123456L);
      assertThat(MoneyUtil.parseAmountToMinor("1 000 000")).isEqualTo(100000000L);
    }

    @Test
    void parsesMixedThousandsAndDecimalEuAndUs() {
      assertThat(MoneyUtil.parseAmountToMinor("1.234,56")).isEqualTo(123456L); // EU
      assertThat(MoneyUtil.parseAmountToMinor("1,234.56")).isEqualTo(123456L); // US
      assertThat(MoneyUtil.parseAmountToMinor("1.234.567")).isEqualTo(123456700L); // EU thousands
      assertThat(MoneyUtil.parseAmountToMinor("1,234,567")).isEqualTo(123456700L); // US thousands
    }

    @Test
    void roundsFractionsBeyondMinorUnitsHalfUp() {
      assertThat(MoneyUtil.parseAmountToMinor("0.005")).isEqualTo(1L);
      assertThat(MoneyUtil.parseAmountToMinor("1.004")).isEqualTo(100L);
      assertThat(MoneyUtil.parseAmountToMinor("9.999")).isEqualTo(1000L); // carry across the unit
    }

    @Test
    void handlesOptionalSigns() {
      assertThat(MoneyUtil.parseAmountToMinor("-12,50")).isEqualTo(-1250L);
      assertThat(MoneyUtil.parseAmountToMinor("+5")).isEqualTo(500L);
    }

    @Test
    void returnsNullForInvalidInput() {
      assertThat(MoneyUtil.parseAmountToMinor("")).isNull();
      assertThat(MoneyUtil.parseAmountToMinor("   ")).isNull();
      assertThat(MoneyUtil.parseAmountToMinor("abc")).isNull();
      assertThat(MoneyUtil.parseAmountToMinor(".")).isNull();
      assertThat(MoneyUtil.parseAmountToMinor("12zł")).isNull(); // "12zł"
      assertThat(MoneyUtil.parseAmountToMinor(null)).isNull();
    }
  }

  @Nested
  class ToBaseMinor {

    @Test
    void roundsToNearestBaseMinorUnit() {
      assertThat(MoneyUtil.toBaseMinor(1000, new BigDecimal("1"))).isEqualTo(1000L);
      assertThat(MoneyUtil.toBaseMinor(1000, new BigDecimal("4.3567"))).isEqualTo(4357L); // 4356.7
      assertThat(MoneyUtil.toBaseMinor(199, new BigDecimal("0.23"))).isEqualTo(46L); // 45.77
    }
  }

  @Nested
  class SumMinor {

    @Test
    void addsIntegersExactly() {
      assertThat(MoneyUtil.sumMinor(List.of(1999L, 1L, 5000L))).isEqualTo(7000L);
      assertThat(MoneyUtil.sumMinor(List.of())).isEqualTo(0L);
    }
  }

  @Nested
  class IsValidAmountInput {

    @Test
    void acceptsPositiveRejectsZeroAndGarbage() {
      assertThat(MoneyUtil.isValidAmountInput("19,99")).isTrue();
      assertThat(MoneyUtil.isValidAmountInput("0,01")).isTrue();
      assertThat(MoneyUtil.isValidAmountInput("0")).isFalse();
      assertThat(MoneyUtil.isValidAmountInput("")).isFalse();
      assertThat(MoneyUtil.isValidAmountInput("abc")).isFalse();
    }
  }

  @Nested
  class Format {

    private static final Locale PL = Locale.forLanguageTag("pl-PL");

    @Test
    void formatsPlnWithSymbol() {
      String formatted = MoneyUtil.formatMinor(1999, "PLN", PL);
      assertThat(formatted).contains("19,99");
      assertThat(formatted).contains("zł"); // "zł"
    }

    @Test
    void formatsPlainGroupedDecimal() {
      // pl-PL groups with a (narrow) no-break space; normalize it to a regular space to assert.
      String grouped = MoneyUtil.formatMinorPlain(500000, PL).replace(' ', ' ').replace(' ', ' ');
      assertThat(grouped).contains("5 000");
    }
  }
}
