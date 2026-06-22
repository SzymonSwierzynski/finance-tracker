package com.financetracker.common.error;

/**
 * Service-level semantic validation failure (HTTP 422) — the request is syntactically valid but
 * violates a business rule that Bean Validation can't express (e.g. a transfer without a counter
 * account, or a foreign-currency amount with no resolvable rate to base).
 */
public class UnprocessableEntityException extends RuntimeException {
  public UnprocessableEntityException(String message) {
    super(message);
  }
}
