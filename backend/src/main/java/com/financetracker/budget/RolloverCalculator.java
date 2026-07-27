package com.financetracker.budget;

import java.time.YearMonth;
import java.util.Map;

/**
 * The floored, compounding budget carry (design §2). {@code carriedIn(targetMonth)} folds every
 * month in {@code [creationMonth, targetMonth)}: each step, the month's available amount (limit +
 * carry so far) minus its spend rolls forward, floored at zero so overspending never creates
 * carried debt. Months absent from {@code spentByMonth} count as zero spend. Pure — no Spring, no
 * persistence.
 */
public final class RolloverCalculator {

  private RolloverCalculator() {}

  public static long carriedIn(
      YearMonth creationMonth,
      YearMonth targetMonth,
      long limit,
      Map<YearMonth, Long> spentByMonth) {
    long carry = 0L;
    for (YearMonth m = creationMonth; m.isBefore(targetMonth); m = m.plusMonths(1)) {
      long spent = spentByMonth.getOrDefault(m, 0L);
      carry = Math.max(0L, limit + carry - spent);
    }
    return carry;
  }
}
