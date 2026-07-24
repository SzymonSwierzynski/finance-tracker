package com.financetracker.importing.dto;

import com.financetracker.importing.AmountMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * How to interpret a CSV against its columns — the shape remembered per account as an import
 * profile. {@code delimiter}/{@code encoding} may be blank to auto-detect / default to UTF-8;
 * {@code dateFormat} may be {@code "auto"}. Column indexes are 0-based; an unused index (e.g. the
 * debit/credit columns in signed mode) is {@code -1}.
 */
public record ImportMapping(
    @Size(max = 4) String delimiter,
    @Size(max = 40) String encoding,
    boolean hasHeader,
    int dateIndex,
    @Size(max = 40) String dateFormat,
    int descriptionIndex,
    @NotNull AmountMode amountMode,
    int amountIndex,
    boolean expenseIsNegative,
    int debitIndex,
    int creditIndex) {}
