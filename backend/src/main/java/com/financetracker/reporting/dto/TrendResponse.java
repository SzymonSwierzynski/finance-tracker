package com.financetracker.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Income and expense over time: one bucket per period across [from, to], zero-filled so the series
 * is continuous. Totals are base-currency minor units. {@code interval} is {@code month} or {@code
 * week}.
 */
public record TrendResponse(
    LocalDate from, LocalDate to, String interval, String currency, List<TrendBucket> buckets) {

  /** One time bucket. {@code period} is {@code YYYY-MM} (month) or {@code YYYY-WW} (ISO week). */
  public record TrendBucket(String period, long incomeMinor, long expenseMinor) {}
}
