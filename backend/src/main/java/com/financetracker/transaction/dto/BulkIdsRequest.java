package com.financetracker.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A bulk selection of transaction ids (delete / restore). Bounded so a request can't be unbounded.
 */
public record BulkIdsRequest(@NotEmpty @Size(max = 500) List<Long> ids) {}
