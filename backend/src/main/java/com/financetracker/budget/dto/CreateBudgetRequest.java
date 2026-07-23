package com.financetracker.budget.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * New budget. {@code amountMinor} is the monthly limit in the reporting (base) currency's minor
 * units; {@code categoryId} must be an owned expense category with no budget yet.
 */
public record CreateBudgetRequest(@NotNull Long categoryId, @Positive long amountMinor) {}
