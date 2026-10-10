package com.spendio.sharedkernel.money;

import java.time.LocalDate;

/**
 * Convert an amount between two currencies using a rate as of {@code onDate}
 * (ARCHITECTURE.md D.5). Implemented by the fx module in Phase 5; until then accounts
 * treats a missing implementation (cross-currency only) as FX_RATE_UNAVAILABLE so the
 * daily-series and totals conversions can still run at parity in Phase 3.
 */
public interface FxConversionPort {

    ConversionResult convert(long amountMinor, String fromCurrency, String toCurrency, LocalDate onDate);

    /**
     * Same maths as {@link #convert} but with an explicit {@code from->to} rate instead of
     * the daily series - used when the ledger's frozen {@code exchange_rate} is overridden
     * at write time (ARCHITECTURE.md D.8). {@code rate} must be positive; the result carries
     * no {@code rateId} because no fx_rates row was consulted.
     */
    ConversionResult convertWithRate(long amountMinor, String fromCurrency, String toCurrency, java.math.BigDecimal rate);
}