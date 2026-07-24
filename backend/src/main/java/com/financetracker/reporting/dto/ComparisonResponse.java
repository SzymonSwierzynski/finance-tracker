package com.financetracker.reporting.dto;

import java.time.LocalDate;

/**
 * Period-over-period comparison in base (reporting) currency minor units. {@code current} is the
 * requested range; {@code previous} is the same range shifted back one {@code mode} unit (month or
 * year); {@code delta} is {@code current − previous} per field. All money is integer minor units —
 * percentage change is derived at the display edge (never stored/computed as a float here).
 */
public record ComparisonResponse(
    String currency, String mode, PeriodSummary current, PeriodSummary previous, Delta delta) {

  /** Income/expense/net for one period (base minor units). */
  public record PeriodSummary(
      LocalDate from, LocalDate to, long incomeMinor, long expenseMinor, long netMinor) {}

  /** {@code current − previous} for each total (may be negative). */
  public record Delta(long incomeMinor, long expenseMinor, long netMinor) {}
}
