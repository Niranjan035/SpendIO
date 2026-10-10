package com.spendio.transactions.support;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.ConversionResult;
import com.spendio.sharedkernel.money.FxConversionPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Frozen FX values for ledger rows (ARCHITECTURE.md D.8): the column stores
 * base->transaction, the port speaks transaction->base, so every rate is inverted on
 * the way in. Same-currency rows freeze at identity with no fx_rates link.
 */
public final class LedgerFx {

    /** The triple written onto a row: rate, base amount, and the consulted fx rate. */
    public record Freeze(BigDecimal exchangeRate, long baseAmountMinor, Long fxRateId) {
    }

    private LedgerFx() {
    }

    /** Freeze an amount in {@code currency} against the user's base currency. */
    public static Freeze freeze(String baseCurrency, FxConversionPort port, long amountMinor,
            String currency, LocalDate onDate) {
        if (currency.equals(baseCurrency)) {
            return new Freeze(BigDecimal.ONE, amountMinor, null);
        }
        if (port == null) {
            throw unavailable(currency);
        }
        ConversionResult result = port.convert(amountMinor, currency, baseCurrency, onDate);
        return new Freeze(invert(result.rate()), result.targetMinor(), result.rateId());
    }

    /** Amount in {@code toCurrency}; identity when the currencies already match. */
    public static long convert(FxConversionPort port, long amountMinor, String fromCurrency,
            String toCurrency, LocalDate onDate) {
        if (fromCurrency.equals(toCurrency)) {
            return amountMinor;
        }
        if (port == null) {
            throw unavailable(fromCurrency);
        }
        return port.convert(amountMinor, fromCurrency, toCurrency, onDate).targetMinor();
    }

    /** The column stores base->transaction; the port speaks transaction->base. */
    public static BigDecimal invert(BigDecimal rate) {
        if (rate.signum() == 0) {
            throw new BusinessException(ErrorCode.FX_RATE_UNAVAILABLE, "FX rate unavailable");
        }
        return BigDecimal.ONE.divide(rate, 10, RoundingMode.HALF_EVEN);
    }

    private static BusinessException unavailable(String currencyCode) {
        return new BusinessException(ErrorCode.FX_RATE_UNAVAILABLE, "FX rate unavailable")
                .withProperty("currencyCode", currencyCode);
    }
}
