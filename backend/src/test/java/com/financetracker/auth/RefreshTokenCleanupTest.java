package com.financetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The nightly purge deletes only refresh tokens past their expiry, leaving revoked-but-unexpired
 * rows in place (they are what makes reuse detection work). The Testcontainers Postgres is shared
 * with no rollback, so this asserts on <em>our two specific tokens</em>, not a global row count.
 */
class RefreshTokenCleanupTest extends AbstractIntegrationTest {

  @Autowired private RefreshTokenCleanup refreshTokenCleanup;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Test
  void purgesExpiredTokensButKeepsLiveOnes() throws Exception {
    RegisteredUser user = register("purge@example.com", "password123");
    Instant now = Instant.now();
    String expiredHash = "expired-" + user.id();
    String liveHash = "live-" + user.id();

    refreshTokenRepository.saveAndFlush(token(user.id(), expiredHash, now.minusSeconds(3600)));
    refreshTokenRepository.saveAndFlush(token(user.id(), liveHash, now.plusSeconds(3600)));

    refreshTokenCleanup.purgeExpired();

    assertThat(refreshTokenRepository.findByTokenHash(expiredHash)).isEmpty();
    assertThat(refreshTokenRepository.findByTokenHash(liveHash)).isPresent();
  }

  private static RefreshToken token(long userId, String hash, Instant expiresAt) {
    RefreshToken rt = new RefreshToken();
    rt.setUserId(userId);
    rt.setTokenHash(hash);
    rt.setExpiresAt(expiresAt);
    return rt;
  }
}
