package com.financetracker.auth.dto;

/** Public view of a user. */
public record UserProfileResponse(long id, String email, String displayName) {}
