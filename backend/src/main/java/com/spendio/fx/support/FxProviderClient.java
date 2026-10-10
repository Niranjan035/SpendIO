package com.spendio.fx.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Source of daily FX rates (ARCHITECTURE.md D.5). Implementations must be side-effect
 * free: persistence of the returned rows belongs to the refresh job, not the client.
 */
public interface FxProviderClient {

    /** Stable name stored in {@code fx_rates.provider} for the rows this client produces. */
    String name();

    /**
     * Rates for {@code base} against every quote currency the provider knows for
     * {@code date}. An empty map means "provider unavailable / nothing to store".
     */
    Map<String, BigDecimal> fetch(String base, LocalDate date);
}