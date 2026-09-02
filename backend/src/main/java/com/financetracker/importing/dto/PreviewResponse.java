package com.financetracker.importing.dto;

import java.util.List;

/** CSV preview result (see fields). {@code mapping} is what was used — detected or supplied. */
public record PreviewResponse(
    String delimiter,
    boolean misdecoded,
    int totalRows,
    int validRows,
    int duplicateRows,
    long incomeMinor,
    long expenseMinor,
    ImportMapping mapping,
    DetectionInfo detection,
    List<PreviewRow> rows) {}
