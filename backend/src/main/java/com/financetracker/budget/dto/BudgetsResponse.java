package com.financetracker.budget.dto;

import java.util.List;

/**
 * The user's budgets with progress for one month, in base-currency minor units. {@code month} is
 * the resolved {@code YYYY-MM}; {@code currency} is the reporting currency the amounts are in.
 */
public record BudgetsResponse(String month, String currency, List<BudgetProgress> items) {

  /**
   * One budget's progress for the month. {@code spentMinor} rolls subcategory spend into a parent
   * budget (matching the breakdown). {@code remainingMinor} is {@code amount - spent} (negative
   * when overspent); {@code over} is {@code spent > amount}.
   */
  public record BudgetProgress(
      long id,
      long categoryId,
      String categoryName,
      String color,
      long amountMinor,
      long spentMinor,
      long remainingMinor,
      boolean over,
      long version) {}
}
