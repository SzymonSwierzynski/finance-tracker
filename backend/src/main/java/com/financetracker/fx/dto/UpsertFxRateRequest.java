package com.financetracker.fx.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Set or replace the rate for one currency. The currency itself is the path segment, so a rate can
 * be written idempotently without first reading the table.
 */
public record UpsertFxRateRequest(
    @NotNull @Positive BigDecimal rateToBase, @Size(max = 100) String source) {}
