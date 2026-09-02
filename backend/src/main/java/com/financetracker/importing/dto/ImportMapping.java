package com.financetracker.importing.dto;

import com.financetracker.importing.AmountMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * How to interpret a CSV against its columns — remembered per account. {@code delimiter}/{@code
 * encoding} may be blank to auto-detect; {@code dateFormat} may be {@code "auto"}; column indexes
 * are 0-based, an unused index is {@code -1}. {@code headerRowIndex} (null = derive from {@code
 * hasHeader}) skips a mid-file preamble; {@code descriptionIndexes} (null/empty = use {@code
 * descriptionIndex}) joins several columns into the description.
 */
public record ImportMapping(
    @Size(max = 4) String delimiter,
    @Size(max = 40) String encoding,
    boolean hasHeader,
    Integer headerRowIndex,
    int dateIndex,
    @Size(max = 40) String dateFormat,
    int descriptionIndex,
    List<Integer> descriptionIndexes,
    @NotNull AmountMode amountMode,
    int amountIndex,
    boolean expenseIsNegative,
    int debitIndex,
    int creditIndex) {}
