package com.financetracker.fx.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One stored rate. {@code stale} is true when {@code baseCurrency} is no longer the user's
 * reporting currency — the rate is kept (it may still be the right number to re-anchor from) but
 * the resolver refuses to use it until the user confirms a value against the new base.
 */
public record FxRateResponse(
    String currency,
    BigDecimal rateToBase,
    String baseCurrency,
    boolean stale,
    String source,
    Instant lastFetchedAt,
    long version) {}
