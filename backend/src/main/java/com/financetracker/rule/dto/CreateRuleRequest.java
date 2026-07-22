package com.financetracker.rule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * New-rule payload. {@code pattern} is matched case-insensitively as a substring of a transaction's
 * description; {@code categoryId} must reference an owned category; {@code priority} defaults to 0
 * (higher wins).
 */
public record CreateRuleRequest(
    @NotBlank @Size(max = 200) String pattern, @NotNull Long categoryId, Integer priority) {}
