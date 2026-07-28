package com.financetracker.transaction;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Trash retention window + purge cron. Picked up by {@code @ConfigurationPropertiesScan}. */
@ConfigurationProperties(prefix = "app.trash")
public record TrashProperties(
    @DefaultValue("30d") Duration retention, @DefaultValue("0 45 3 * * *") String cleanupCron) {}
