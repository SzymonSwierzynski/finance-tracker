package com.financetracker.export.dto;

import java.math.BigDecimal;

/**
 * One transaction flattened for export: money stays as integer minor units (round-trippable),
 * account/counterAccount/category are the human-readable names ({@code counterAccount} is set only
 * for transfers, else ""), {@code rateToBase} is the locked rate.
 *
 * <p>{@code categoryParent} is the parent category's name (or "" for a top-level/uncategorized). It
 * disambiguates a restore when the same leaf name exists under two parents — the unique key is
 * (parent, name), not the bare name.
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
    String categoryParent,
    String description,
    String note) {}
