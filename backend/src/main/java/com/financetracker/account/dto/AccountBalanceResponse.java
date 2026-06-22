package com.financetracker.account.dto;

/** Current balance for a balance-tracked account, in the account's native-currency minor units. */
public record AccountBalanceResponse(long accountId, String currency, long balanceMinor) {}
