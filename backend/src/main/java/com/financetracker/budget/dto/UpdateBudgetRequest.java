package com.financetracker.budget.dto;

import jakarta.validation.constraints.Positive;

/**
 * Budget update. Only the monthly limit is mutable (re-point a budget by deleting and recreating).
 * {@code version} guards against a stale write (optimistic locking → 409).
 */
public record UpdateBudgetRequest(@Positive long amountMinor, long version, boolean rollover) {}
