package com.financetracker.common.security;

/** The authenticated principal, resolved from the access-token JWT. */
public record AuthUser(long id, String email) {}
