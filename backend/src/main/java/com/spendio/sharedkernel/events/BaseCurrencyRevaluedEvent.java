package com.spendio.sharedkernel.events;

import java.math.BigDecimal;

/**
 * The user's base currency changed and existing derived figures were rescaled by
 * {@code factor} (old base -> new base) (ARCHITECTURE.md D.3, Phase 14). Published
 * from {@code ProfileService} inside the change transaction; listeners revalue their
 * stored base-currency columns with the same factor so every module lands on the new
 * unit together. Listeners must stay synchronous - the whole revaluation is one
 * transaction (D.3).
 */
public record BaseCurrencyRevaluedEvent(
        Long userId,
        String fromCurrency,
        String toCurrency,
        BigDecimal factor) {
}
