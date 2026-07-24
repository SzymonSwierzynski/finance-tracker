package com.financetracker.recurring.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Partial update of a template (PATCH) — the fields safe to change without recomputing the
 * schedule. {@code version} is required (409 on stale); change frequency/start by recreating the
 * template.
 */
public record UpdateRecurringRequest(
    @NotNull Long version,
    @Positive Long amountMinor,
    @Size(max = 500) String description,
    @Size(max = 500) String note,
    Boolean active,
    LocalDate endDate) {}
