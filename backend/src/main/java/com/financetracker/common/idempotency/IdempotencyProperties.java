package com.financetracker.common.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retention window + purge cron. Picked up by {@code @ConfigurationPropertiesScan} on Application.
 */
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(
    @DefaultValue("48h") Duration retention, @DefaultValue("0 30 3 * * *") String cleanupCron) {}
