package com.spendio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} workers (recurring generation, FX refresh, budget rollover,
 * digests - ARCHITECTURE.md A.5). Pool size comes from
 * {@code spring.task.scheduling.pool.size}; multi-instance safety comes from
 * {@code FOR UPDATE SKIP LOCKED} claims in the modules themselves.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
