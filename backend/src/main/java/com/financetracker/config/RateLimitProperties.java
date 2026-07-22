package com.financetracker.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Throttle for the unauthenticated auth endpoints (§5). The defaults allow a human (and a browser
 * refreshing its 15-minute access token) far more headroom than they need, while capping how fast a
 * single source can grind through passwords — each attempt costs a deliberate BCrypt-12 hash, so
 * this protects CPU as much as it protects credentials.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("20") int capacity,
    @DefaultValue("1m") Duration window,
    /** How often idle buckets are evicted; read by {@code @Scheduled} as a placeholder. */
    @DefaultValue("PT10M") Duration sweepInterval) {}
