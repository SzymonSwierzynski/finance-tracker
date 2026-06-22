package com.financetracker.category.dto;

import com.financetracker.category.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * New-category payload. {@code parentId} null means a top-level category; a non-null parent must be
 * a top-level category of the same kind (two levels only). {@code color} defaults when omitted.
 */
public record CreateCategoryRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull CategoryKind kind,
    Long parentId,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a #RRGGBB hex color") String color) {}
