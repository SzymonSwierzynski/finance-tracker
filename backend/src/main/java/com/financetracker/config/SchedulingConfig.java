package com.financetracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} housekeeping jobs (currently the refresh-token purge). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
