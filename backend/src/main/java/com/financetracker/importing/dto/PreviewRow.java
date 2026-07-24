package com.financetracker.importing.dto;

import com.financetracker.transaction.TransactionType;
import java.time.LocalDate;

/**
 * One previewed CSV row: the interpreted values plus flags the UI needs — {@code valid} (with a
 * human {@code error}) and {@code duplicate} (matches an existing transaction or an earlier row in
 * this same file).
 */
public record PreviewRow(
    int index,
    LocalDate date,
    Long amountMinor,
    TransactionType type,
    String description,
    boolean valid,
    String error,
    boolean duplicate) {}
