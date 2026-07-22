package com.financetracker.importing;

import com.financetracker.transaction.TransactionType;
import java.time.LocalDate;

/**
 * A CSV row interpreted against a mapping, ready for preview/commit. Pure output of {@link
 * ImportRowBuilder}: {@code date}/{@code amountMinor} are null when unparseable, and {@code valid}
 * is false with a human {@code error} so the UI can flag the row before importing. {@code type} is
 * only ever expense or income (imports never create transfers).
 */
public record ParsedImportRow(
    int index,
    LocalDate date,
    Long amountMinor,
    TransactionType type,
    String description,
    boolean valid,
    String error) {}
