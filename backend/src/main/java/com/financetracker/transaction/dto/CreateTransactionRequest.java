package com.financetracker.transaction.dto;

import com.financetracker.transaction.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * New-transaction payload. {@code amountMinor} is positive native-currency minor units. {@code
 * currency} defaults to the account's currency when omitted. {@code rateToBase} is optional: the
 * service resolves and locks it (1 for the reporting currency). Transfers require {@code
 * counterAccountId}; categories arrive in Phase 3, so transactions start uncategorized.
 */
public record CreateTransactionRequest(
    @NotNull LocalDate date,
    @NotNull @Positive Long amountMinor,
    @NotNull TransactionType type,
    @NotNull Long accountId,
    Long counterAccountId,
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO 4217 currency code")
        String currency,
    @Positive BigDecimal rateToBase,
    @Size(max = 500) String description,
    @Size(max = 1000) String note) {}
