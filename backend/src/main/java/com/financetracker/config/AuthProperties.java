package com.financetracker.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Auth configuration: token lifetimes, the refresh-cookie attributes, and the RSA signing key.
 * Secrets come from the environment in prod; dev/test fall back to an ephemeral generated key.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    @DefaultValue("15m") Duration accessTokenTtl,
    @DefaultValue("30d") Duration refreshTokenTtl,
    @DefaultValue("finance-tracker") String issuer,
    /** Cron for the expired-refresh-token purge; read by {@code @Scheduled} as a placeholder. */
    @DefaultValue("0 15 3 * * *") String tokenCleanupCron,
    @DefaultValue Cookie cookie,
    @DefaultValue Jwt jwt) {

  /** Attributes of the HttpOnly refresh-token cookie. */
  public record Cookie(
      @DefaultValue("refreshToken") String name,
      @DefaultValue("/api/v1/auth") String path,
      @DefaultValue("false") boolean secure,
      @DefaultValue("Lax") String sameSite,
      String domain) {}

  /** RSA key material for signing/verifying access tokens. PEM (or bare base64) from env. */
  public record Jwt(@DefaultValue("ft-dev-1") String keyId, String privateKey, String publicKey) {}
}
