package com.financetracker.budget.dto;

/** A budget's stored fields (returned on create/update). Amount is base-currency minor units. */
public record BudgetResponse(
    long id, long categoryId, long amountMinor, long version, boolean rollover) {}
