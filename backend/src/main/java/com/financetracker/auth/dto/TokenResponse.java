package com.financetracker.auth.dto;

/**
 * Successful auth response. The access token is short-lived and held in memory by the client; the
 * refresh token is delivered out-of-band in an HttpOnly cookie, never in this body.
 */
public record TokenResponse(
    String accessToken, String tokenType, long expiresInSeconds, UserProfileResponse user) {}
