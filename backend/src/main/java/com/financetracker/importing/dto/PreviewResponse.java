package com.financetracker.importing.dto;

import java.util.List;

/**
 * CSV preview result. {@code delimiter} is what was used (possibly auto-detected); {@code
 * misdecoded} warns that the chosen encoding produced replacement characters. Counts summarize the
 * data rows: how many are valid and how many of those duplicate existing/earlier rows.
 */
public record PreviewResponse(
    String delimiter,
    boolean misdecoded,
    int totalRows,
    int validRows,
    int duplicateRows,
    List<PreviewRow> rows) {}
