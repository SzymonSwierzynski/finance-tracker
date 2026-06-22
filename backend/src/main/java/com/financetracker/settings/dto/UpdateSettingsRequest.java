package com.financetracker.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Settings update payload. Currency is an ISO 4217 alpha-3 code (normalized to upper-case). */
public record UpdateSettingsRequest(
    @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO 4217 currency code")
        String reportingCurrency) {}
