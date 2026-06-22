package com.financetracker.category.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial category update (PATCH): rename / recolor. Kind and parent are immutable once set. {@code
 * version} is required for optimistic locking (409 on stale).
 */
public record UpdateCategoryRequest(
    @NotNull Long version,
    @Size(max = 100) String name,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a #RRGGBB hex color") String color) {}
