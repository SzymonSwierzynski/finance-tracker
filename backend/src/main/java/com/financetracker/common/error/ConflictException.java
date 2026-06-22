package com.financetracker.common.error;

/** Thrown on a state conflict (e.g. duplicate email, dedupe hit). Mapped to {@code 409}. */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
