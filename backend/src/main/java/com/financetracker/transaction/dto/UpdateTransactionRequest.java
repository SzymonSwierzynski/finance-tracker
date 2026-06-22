package com.financetracker.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Partial transaction update (PATCH); only non-null fields apply. {@code version} is required for
 * optimistic locking (409 on stale). The locked {@code rateToBase}, currency, type and accounts are
 * immutable here — editing amount or date recomputes the dedupe hash.
 */
public record UpdateTransactionRequest(
    @NotNull Long version,
    LocalDate date,
    @Positive Long amountMinor,
    @Size(max = 500) String description,
    @Size(max = 1000) String note) {}
