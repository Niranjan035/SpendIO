package com.spendio.fx.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Provider stub used when no HTTP provider is configured (ARCHITECTURE.md D.5): it
 * reports no rates, so the refresh job stores nothing and manual rates keep working.
 */
public class NoopFxProviderClient implements FxProviderClient {

    private final String name;

    public NoopFxProviderClient() {
        this("noop");
    }

    public NoopFxProviderClient(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, BigDecimal> fetch(String base, LocalDate date) {
        return Map.of();
    }
}