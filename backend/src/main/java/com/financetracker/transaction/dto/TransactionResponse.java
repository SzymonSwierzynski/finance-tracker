package com.financetracker.transaction.dto;

import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Transaction as exposed to clients. {@code amountMinor} is native-currency minor units; {@code
 * baseMinor} is the locked base-currency value ({@code round(amountMinor * rateToBase)}) so the UI
 * never has to convert. All money is integer minor units.
 */
public record TransactionResponse(
    long id,
    LocalDate date,
    long amountMinor,
    TransactionType type,
    long accountId,
    Long counterAccountId,
    Long categoryId,
    String currency,
    BigDecimal rateToBase,
    long baseMinor,
    String description,
    String note,
    Long importBatchId,
    String dedupeHash,
    long version) {}
