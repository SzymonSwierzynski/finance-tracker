package com.financetracker.common.idempotency;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly purge of idempotency keys older than the retention window (mirrors {@code
 * RefreshTokenCleanup}). The delete is idempotent, so running it on every scaled-out instance is
 * harmless.
 */
@Component
public class IdempotencyKeyCleanup {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanup.class);

  private final IdempotencyKeyRepository repository;
  private final IdempotencyProperties properties;

  public IdempotencyKeyCleanup(
      IdempotencyKeyRepository repository, IdempotencyProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  @Scheduled(cron = "${app.idempotency.cleanup-cron:0 30 3 * * *}")
  @Transactional
  public void purgeOld() {
    int deleted = repository.deleteCreatedBefore(Instant.now().minus(properties.retention()));
    if (deleted > 0) {
      log.info("Purged {} old idempotency key(s)", deleted);
    }
  }
}
