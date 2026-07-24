package com.financetracker.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Income vs expense over time with a running net accumulated across the range. Totals are
 * base-currency minor units; {@code interval} is {@code month} or {@code week}.
 */
public record CashflowResponse(
    LocalDate from, LocalDate to, String interval, String currency, List<CashflowBucket> buckets) {

  public record CashflowBucket(
      String period, long incomeMinor, long expenseMinor, long netMinor, long runningNetMinor) {}
}
