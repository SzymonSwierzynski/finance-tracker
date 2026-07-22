package com.financetracker.recurring.dto;

import com.financetracker.recurring.RecurringFrequency;
import com.financetracker.transaction.TransactionType;
import java.time.LocalDate;

/** A recurring template as exposed to clients. */
public record RecurringResponse(
    long id,
    long accountId,
    Long categoryId,
    long amountMinor,
    TransactionType type,
    String currency,
    String description,
    String note,
    RecurringFrequency frequency,
    int intervalCount,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate nextRunDate,
    boolean active,
    long version) {}
