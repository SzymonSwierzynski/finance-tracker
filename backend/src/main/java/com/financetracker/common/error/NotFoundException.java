package com.financetracker.common.error;

/**
 * Thrown when a requested resource does not exist OR is not owned by the current user. Mapped to
 * {@code 404} so cross-user access never leaks the existence of another user's row (§4 of the
 * contract).
 */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }

  public static NotFoundException of(String resource, Object id) {
    return new NotFoundException(resource + " " + id + " was not found");
  }
}
