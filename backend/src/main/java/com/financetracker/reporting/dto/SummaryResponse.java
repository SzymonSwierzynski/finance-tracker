package com.financetracker.reporting.dto;

import java.time.LocalDate;

/**
 * Period income/expense/net for a date range, in base (reporting) currency minor units. Transfers
 * are excluded. All totals are integer minor units (formatting happens at the client edge).
 */
public record SummaryResponse(
    LocalDate from,
    LocalDate to,
    String currency,
    long incomeMinor,
    long expenseMinor,
    long netMinor) {}
