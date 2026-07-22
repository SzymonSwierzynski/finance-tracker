package com.financetracker.auth;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic purge of expired refresh tokens. Without it the table grows without bound: rotation
 * writes a new row on every refresh and revoked rows are kept (deliberately — see {@link
 * AuthService#refresh}) until they expire.
 *
 * <p>Only rows past {@code expires_at} are removed, so reuse detection keeps working for the whole
 * lifetime of a token. Every instance in a scaled-out deployment will run this; the delete is
 * idempotent, so that is harmless — a distributed lock only becomes worthwhile if the sweep gets
 * expensive.
 */
@Component
public class RefreshTokenCleanup {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

  private final RefreshTokenRepository refreshTokenRepository;

  public RefreshTokenCleanup(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  /** Runs nightly by default; the cron is overridable per environment. */
  @Scheduled(cron = "${app.auth.token-cleanup-cron:0 15 3 * * *}")
  @Transactional
  public void purgeExpired() {
    int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now());
    if (deleted > 0) {
      log.info("Purged {} expired refresh token(s)", deleted);
    }
  }
}
