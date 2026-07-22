package com.financetracker.recurring.dto;

import com.financetracker.recurring.RecurringFrequency;
import com.financetracker.transaction.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * New recurring template. {@code currency} defaults to the account's; {@code categoryId} must match
 * the type's kind; {@code intervalCount} defaults to 1. Only expense/income are allowed.
 */
public record CreateRecurringRequest(
    @NotNull Long accountId,
    @Positive long amountMinor,
    @NotNull TransactionType type,
    Long categoryId,
    @Size(max = 3) String currency,
    @Size(max = 500) String description,
    @Size(max = 500) String note,
    @NotNull RecurringFrequency frequency,
    @Positive Integer intervalCount,
    @NotNull LocalDate startDate,
    LocalDate endDate) {}
