package com.financetracker.export.dto;

import java.math.BigDecimal;

/**
 * One transaction flattened for export: money stays as integer minor units (round-trippable),
 * account/category are the human-readable names, {@code rateToBase} is the locked rate.
 */
public record ExportedTransaction(
    String date,
    String type,
    long amountMinor,
    String currency,
    BigDecimal rateToBase,
    String account,
    String category,
    String description,
    String note) {}
