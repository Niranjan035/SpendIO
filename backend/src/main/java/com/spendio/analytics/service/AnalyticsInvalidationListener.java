package com.spendio.analytics.service;

import com.spendio.sharedkernel.events.BaseCurrencyRevaluedEvent;
import com.spendio.sharedkernel.events.LedgerChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps analytics answers honest (ARCHITECTURE.md D.14): any ledger write or base
 * currency revaluation for a user evicts that user's cached analytics. Listens on
 * kernel events so the transactions module never has to know analytics exists (B.3).
 */
@Component
public class AnalyticsInvalidationListener {

    private final AnalyticsCacheManager cache;

    public AnalyticsInvalidationListener(AnalyticsCacheManager cache) {
        this.cache = cache;
    }

    @EventListener
    public void onLedgerChanged(LedgerChangedEvent event) {
        cache.evictUser(event.userId());
    }

    @EventListener
    public void onBaseCurrencyRevalued(BaseCurrencyRevaluedEvent event) {
        cache.evictUser(event.userId());
    }
}
