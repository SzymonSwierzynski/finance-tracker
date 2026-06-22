package com.financetracker.reporting.dto;

import com.financetracker.category.CategoryKind;
import java.time.LocalDate;
import java.util.List;

/**
 * Category breakdown for a period, in base-currency minor units. Subcategory spend rolls up to its
 * parent; a parent that also has direct spend surfaces a synthetic "(direct)" child so its
 * drill-down still sums to the parent total. Uncategorized is its own slice (null categoryId).
 */
public record BreakdownResponse(
    CategoryKind kind,
    LocalDate from,
    LocalDate to,
    String currency,
    long totalBaseMinor,
    long count,
    List<BreakdownParent> parents) {

  /** A top-level slice. {@code categoryId} is null for the Uncategorized bucket. */
  public record BreakdownParent(
      Long categoryId,
      String name,
      String color,
      long baseMinor,
      double share,
      List<BreakdownChild> children) {}

  /** A subcategory split (or the parent's own "(direct)" slice). */
  public record BreakdownChild(Long categoryId, String name, long baseMinor, double share) {}
}
