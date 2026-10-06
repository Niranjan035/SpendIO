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
 */
@Configuration
public class CacheConfig {

    static final String CURRENCIES = "currencies";
    static final String FX_RATES = "fxRates";
    static final String ANALYTICS = "analytics";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(CURRENCIES, buildCache(Duration.ofHours(1)));
        manager.registerCustomCache(FX_RATES, buildCache(Duration.ofHours(1)));
        manager.registerCustomCache(ANALYTICS, buildCache(Duration.ofSeconds(120)));
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
