package com.financetracker.account.dto;

import com.financetracker.account.AccountType;

/**
 * Account as exposed to clients. Money is BIGINT minor units; {@code version} backs 409 on stale
 * writes.
 */
public record AccountResponse(
    long id,
    String name,
    AccountType type,
    String currency,
    Long startingBalanceMinor,
    boolean trackBalance,
    boolean archived,
    long version) {}
