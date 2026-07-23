package com.financetracker.export.dto;

import java.math.BigDecimal;

/**
 * One transaction flattened for export: money stays as integer minor units (round-trippable),
 * account/counterAccount/category are the human-readable names ({@code counterAccount} is set only
 * for transfers, else ""), {@code rateToBase} is the locked rate.
 */
public record ExportedTransaction(
    String date,
    String type,
    long amountMinor,
    String currency,
    BigDecimal rateToBase,
    String account,
    String counterAccount,
    String category,
    String description,
    String note) {}
