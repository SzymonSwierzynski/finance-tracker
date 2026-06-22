package com.financetracker.category.dto;

/** Result of deleting a category: how many transactions were uncategorized as a side effect. */
public record DeleteCategoryResponse(long uncategorizedTransactions) {}
