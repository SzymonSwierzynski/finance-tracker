package com.financetracker.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Partial transaction update (PATCH). {@code version} is required for optimistic locking (409 on
 * stale). The locked {@code rateToBase}, currency, type and accounts are immutable here — editing
 * amount or date recomputes the dedupe hash. {@code categoryId} is applied as given (its value,
 * including {@code null} to uncategorize), so the edit form should always send the intended value.
 */
public record UpdateTransactionRequest(
    @NotNull Long version,
    LocalDate date,
    @Positive Long amountMinor,
    Long categoryId,
    @Size(max = 500) String description,
    @Size(max = 1000) String note) {}
