package com.financetracker.account.dto;

import com.financetracker.account.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * New-account payload. {@code trackBalance} defaults to false; {@code startingBalanceMinor} is only
 * retained when balance tracking is on. Currency is an ISO 4217 alpha-3 code (upper-cased by the
 * service).
 */
public record CreateAccountRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull AccountType type,
    @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO 4217 currency code")
        String currency,
    Boolean trackBalance,
    Long startingBalanceMinor) {}
