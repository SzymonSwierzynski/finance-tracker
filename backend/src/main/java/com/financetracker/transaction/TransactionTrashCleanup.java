package com.financetracker.transaction;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly purge of trashed transactions past the retention window (mirrors {@code
 * RefreshTokenCleanup}). Idempotent, so running on every scaled-out instance is harmless.
 */
@Component
public class TransactionTrashCleanup {

  private static final Logger log = LoggerFactory.getLogger(TransactionTrashCleanup.class);

  private final TransactionRepository transactionRepository;
  private final TrashProperties properties;

  public TransactionTrashCleanup(
      TransactionRepository transactionRepository, TrashProperties properties) {
    this.transactionRepository = transactionRepository;
    this.properties = properties;
  }

  @Scheduled(cron = "${app.trash.cleanup-cron:0 45 3 * * *}")
  @Transactional
  public void purgeExpired() {
    int deleted =
        transactionRepository.deleteByDeletedAtBefore(Instant.now().minus(properties.retention()));
    if (deleted > 0) {
      log.info("Purged {} trashed transaction(s)", deleted);
    }
  }
}
