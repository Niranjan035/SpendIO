package com.spendio.fx.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Caffeine cache for resolved rates keyed {@code (from,to,date)} plus the rate limiter
 * for on-demand provider fetches (ARCHITECTURE.md D.5). The refresh job invalidates the
 * whole cache after it stores new rows.
 */
@Service
public class FxCacheService {

    private final Cache<String, Optional<ResolvedRate>> cache;
    private final AtomicLong lastFetchAt = new AtomicLong(0);
    private final long fetchIntervalMillis;
    private final boolean enabled;

    public FxCacheService(
            @Value("${spendio.fx.cache-enabled:true}") boolean enabled,
            @Value("${spendio.fx.on-demand-interval-seconds:60}") long onDemandIntervalSeconds) {
        this.enabled = enabled;
        this.fetchIntervalMillis = Math.max(0, onDemandIntervalSeconds) * 1000;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(6))
                .build();
    }

    public Optional<ResolvedRate> get(String key) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<ResolvedRate> cached = cache.getIfPresent(key);
        return cached == null ? Optional.empty() : cached;
    }

    public void put(String key, Optional<ResolvedRate> value) {
        if (enabled) {
            cache.put(key, value);
        }
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    /** True when an on-demand provider fetch is allowed right now (rate limited). */
    public boolean tryReserveFetch() {
        long now = System.currentTimeMillis();
        long last = lastFetchAt.get();
        if (now - last < fetchIntervalMillis) {
            return false;
        }
        return lastFetchAt.compareAndSet(last, now);
    }

    public static String key(String from, String to, java.time.LocalDate date) {
        return from.toUpperCase() + "|" + to.toUpperCase() + "|" + date;
    }
}