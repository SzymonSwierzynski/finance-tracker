package com.financetracker.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Date advancement for every frequency (only {@code monthly} is exercised by the materializer
 * tests) plus the token round-trip. Month/year steps clamp to the shorter month, per {@link
 * LocalDate#plusMonths}.
 */
class RecurringFrequencyTest {

  private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);

  @Test
  void advancesByEachFrequency() {
    assertThat(RecurringFrequency.DAILY.advance(JAN_15, 3)).isEqualTo(LocalDate.of(2026, 1, 18));
    assertThat(RecurringFrequency.WEEKLY.advance(JAN_15, 2)).isEqualTo(LocalDate.of(2026, 1, 29));
    assertThat(RecurringFrequency.MONTHLY.advance(JAN_15, 1)).isEqualTo(LocalDate.of(2026, 2, 15));
    assertThat(RecurringFrequency.YEARLY.advance(JAN_15, 1)).isEqualTo(LocalDate.of(2027, 1, 15));
  }

  @Test
  void monthStepClampsToMonthEnd() {
    assertThat(RecurringFrequency.MONTHLY.advance(LocalDate.of(2026, 1, 31), 1))
        .isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void valueAndFromValueRoundTrip() {
    for (RecurringFrequency f : RecurringFrequency.values()) {
      assertThat(RecurringFrequency.fromValue(f.value())).isEqualTo(f);
    }
  }

  @Test
  void fromValueRejectsUnknownToken() {
    assertThatThrownBy(() -> RecurringFrequency.fromValue("fortnightly"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
