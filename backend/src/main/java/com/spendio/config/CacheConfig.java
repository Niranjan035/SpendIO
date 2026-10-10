package com.spendio.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process caches (ARCHITECTURE.md A.6): reference data stays hot for a release,
 * FX rates ride out provider outages, and analytics answers are short-lived.
 * Personal cache keys always include {@code userId} (invalidation happens in the modules).
 *
 * <p>Spring Boot's Actuator auto-instruments every cache on the {@link CacheManager}
 * ({@code cache.size}, {@code cache.gets}, ...), so the metrics show up on the dashboards
 * in {@code infra/monitoring/} without any manual registration here.
 */
@Configuration
public class CacheConfig {

    public static final String CURRENCIES = "currencies";
    public static final String FX_RATES = "fxRates";
    public static final String ANALYTICS = "analytics";

    private static final Duration REFERENCE_TTL = Duration.ofHours(1);
    private static final Duration ANALYTICS_TTL = Duration.ofSeconds(120);

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(CURRENCIES, buildCache(REFERENCE_TTL));
        manager.registerCustomCache(FX_RATES, buildCache(REFERENCE_TTL));
        manager.registerCustomCache(ANALYTICS, buildCache(ANALYTICS_TTL));
        return manager;
    }

    private static Cache<Object, Object> buildCache(Duration ttl) {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();
    }
}
