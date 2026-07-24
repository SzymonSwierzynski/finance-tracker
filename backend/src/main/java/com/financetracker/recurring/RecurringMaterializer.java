package com.financetracker.recurring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly sweep that materializes every active, due recurring template across all users, so
 * schedules advance without anyone opening the app. The user-facing {@code POST /recurring/run}
 * does the same for one user on demand. The cross-user query is a system concern (not
 * request-scoped), like {@code RefreshTokenCleanup}; the work is idempotent per schedule, so every
 * instance in a scaled-out deployment running it is harmless.
 */
@Component
public class RecurringMaterializer {

  private static final Logger log = LoggerFactory.getLogger(RecurringMaterializer.class);

  private final RecurringTransactionService recurringService;

  public RecurringMaterializer(RecurringTransactionService recurringService) {
    this.recurringService = recurringService;
  }

  /** Runs nightly by default; the cron is overridable per environment. */
  @Scheduled(cron = "${app.recurring.materialize-cron:0 30 3 * * *}")
  public void materializeDue() {
    int created = materializeAll();
    if (created > 0) {
      log.info("Materialized {} recurring transaction(s)", created);
    }
  }

  /**
   * Materialize each due template in its own transaction, so one template's optimistic-lock
   * conflict (e.g. a user hitting {@code /recurring/run} mid-sweep) is isolated and logged rather
   * than rolling back — and silently skipping — every user's schedule for the night. Returns the
   * total number of transactions created across all templates.
   */
  int materializeAll() {
    int total = 0;
    for (Long id : recurringService.dueTemplateIds()) {
      try {
        total += recurringService.materializeTemplate(id);
      } catch (RuntimeException e) {
        log.warn("Skipping recurring template {} this run: {}", id, e.toString());
      }
    }
    return total;
  }
}
