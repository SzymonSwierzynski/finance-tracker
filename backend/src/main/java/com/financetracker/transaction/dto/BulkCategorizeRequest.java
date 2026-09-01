package com.financetracker.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Bulk recategorize: set {@code categoryId} (null = uncategorize) on the selected transactions. */
public record BulkCategorizeRequest(@NotEmpty @Size(max = 500) List<Long> ids, Long categoryId) {}
