package com.financetracker.rule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Partial rule update (PATCH). {@code version} is required for optimistic locking (409 on stale);
 * any of pattern / categoryId / priority may be supplied and the rest are left unchanged.
 */
public record UpdateRuleRequest(
    @NotNull Long version, @Size(max = 200) String pattern, Long categoryId, Integer priority) {}
