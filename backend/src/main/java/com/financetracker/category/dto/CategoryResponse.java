package com.financetracker.category.dto;

import com.financetracker.category.CategoryKind;

/** Category as exposed to clients. {@code parentId} is null for top-level categories. */
public record CategoryResponse(
    long id, String name, CategoryKind kind, Long parentId, String color, long version) {}
