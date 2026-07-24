package com.financetracker.reporting.dto;

import com.financetracker.category.CategoryKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Spending over time broken down by top-level category, for a stacked chart. {@code series} lists
 * the stacks (top-level categories with spend, plus an Uncategorized entry) ordered by total
 * descending; each bucket's {@code amounts} maps a series key (the category id as text, or {@code
 * "uncategorized"}) to base-currency minor units. Buckets are zero-filled across the range.
 */
public record CategoryTrendResponse(
    LocalDate from,
    LocalDate to,
    String interval,
    String currency,
    CategoryKind kind,
    List<CategorySeries> series,
    List<CategoryTrendBucket> buckets) {

  public record CategorySeries(Long categoryId, String name, String color) {}

  public record CategoryTrendBucket(String period, Map<String, Long> amounts) {}
}
