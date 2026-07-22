package com.financetracker.recurring.dto;

/** Result of materializing due templates: how many transactions were created. */
public record RunRecurringResponse(int materialized) {}
