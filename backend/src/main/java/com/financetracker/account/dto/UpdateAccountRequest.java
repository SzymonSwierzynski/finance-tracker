package com.financetracker.account.dto;

import com.financetracker.account.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Partial account update (PATCH). Only non-null fields are applied. {@code version} is required and
 * checked for optimistic locking — a stale value yields 409. Currency is immutable once set.
 */
public record UpdateAccountRequest(
    @NotNull Long version,
    @Size(max = 100) String name,
    AccountType type,
    Boolean trackBalance,
    Long startingBalanceMinor) {}
