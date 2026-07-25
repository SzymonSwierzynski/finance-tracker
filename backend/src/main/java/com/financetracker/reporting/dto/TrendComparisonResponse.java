package com.financetracker.reporting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.financetracker.reporting.dto.ComparisonResponse.Delta;
import com.financetracker.reporting.dto.ComparisonResponse.PeriodSummary;
import java.util.List;

/**
 * Trends period-comparison in base (reporting) currency minor units. {@code current} is the
 * requested range; {@code previous} is the immediately-preceding range of equal length; {@code
 * delta} is {@code current − previous} per total. {@code movers} are expense categories rolled up
 * to their top-level parent, ordered by absolute change (biggest first). Percentages, and the
 * new/gone labels, are derived at the display edge — never stored as floats here.
 */
public record TrendComparisonResponse(
    String currency,
    PeriodSummary current,
    PeriodSummary previous,
    Delta delta,
    List<CategoryMover> movers) {

  /**
   * One top-level expense category's spend now vs the previous period. {@code categoryId} null =
   * Uncategorized.
   */
  public record CategoryMover(
      Long categoryId, String name, String color, long currentMinor, long previousMinor) {

    /** {@code currentMinor − previousMinor}; derived so it can never desync from the two totals. */
    @JsonProperty
    public long deltaMinor() {
      return currentMinor - previousMinor;
    }
  }
}
