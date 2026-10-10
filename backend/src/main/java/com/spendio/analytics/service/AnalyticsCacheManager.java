package com.spendio.analytics.service;

import com.spendio.config.CacheConfig;
import java.util.function.Supplier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Per-user analytics response cache (ARCHITECTURE.md D.14): keys are
 * {@code userId:endpoint:params}, TTL comes from the {@code analytics} Caffeine cache
 * registered in {@link CacheConfig}, and any ledger write for a user evicts exactly
 * that user's entries ({@link AnalyticsInvalidationListener}).
 */
@Component
public class AnalyticsCacheManager {

    private final Cache cache;

    public AnalyticsCacheManager(CacheManager cacheManager) {
        this.cache = cacheManager.getCache(CacheConfig.ANALYTICS);
    }

    public <T> T get(Long userId, String key, Class<T> type, Supplier<T> loader) {
        String fullKey = userId + ":" + key;
        Cache.ValueWrapper wrapper = cache.get(fullKey);
        if (wrapper != null && wrapper.get() != null) {
            return type.cast(wrapper.get());
        }
        T value = loader.get();
        if (value != null) {
            cache.put(fullKey, value);
        }
        return value;
    }

    /** Drops every cached answer belonging to one user. */
    public void evictUser(Long userId) {
        String prefix = userId + ":";
        if (cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache) {
            nativeCache.asMap().keySet().removeIf(key -> key instanceof String s && s.startsWith(prefix));
        } else {
            cache.clear();
        }
    }
}
