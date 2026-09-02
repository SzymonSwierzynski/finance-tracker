package com.financetracker.importing.detect;

import com.financetracker.importing.AmountMode;
import java.util.List;
import java.util.Map;

/**
 * The outcome of column-role detection for one header row. {@code descriptionIndexes} may hold
 * several columns (joined by a space at build time). {@code recognizedColumns} maps each resolved
 * role name to its raw header label, for the UI banner. Unused indexes are -1.
 */
public record DetectedMapping(
    int dateIndex,
    String dateFormat,
    AmountMode amountMode,
    int amountIndex,
    boolean expenseIsNegative,
    int debitIndex,
    int creditIndex,
    List<Integer> descriptionIndexes,
    Map<String, String> recognizedColumns) {}
