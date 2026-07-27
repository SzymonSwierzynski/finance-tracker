package com.financetracker.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The floored, compounding carry — the §2 design table, asserted deterministically. */
class RolloverCalculatorTest {

  private static final YearMonth JAN = YearMonth.of(2026, 1);
  private static final long LIMIT = 50000; // 500.00

  // Spend per month (minor): Jan 400, Feb 550, Mar 620, Apr 300.
  private static final Map<YearMonth, Long> SPEND =
      Map.of(
          JAN,
          40000L,
          JAN.plusMonths(1),
          55000L,
          JAN.plusMonths(2),
          62000L,
          JAN.plusMonths(3),
          30000L);

  @Test
  void foldsUnspentBudgetForwardWithAFloor() {
    // carriedIn(m) folds months [creation, m). Creation = JAN.
    assertThat(RolloverCalculator.carriedIn(JAN, JAN, LIMIT, SPEND)).isZero(); // creation month
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(1), LIMIT, SPEND)).isEqualTo(10000);
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(2), LIMIT, SPEND)).isEqualTo(5000);
    // Mar overspends (620 > 550 available) → carry floored at 0, not -70.
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(3), LIMIT, SPEND)).isZero();
    // Apr underspends from a fresh 500 → 200 carried into May.
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(4), LIMIT, SPEND)).isEqualTo(20000);
  }

  @Test
  void targetAtOrBeforeCreationCarriesZero() {
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.minusMonths(1), LIMIT, SPEND)).isZero();
  }

  @Test
  void monthsWithNoSpendAccrueTheFullLimit() {
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(3), LIMIT, Map.of()))
        .isEqualTo(150000); // three empty months × 500.00
  }
}
