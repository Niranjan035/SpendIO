package com.spendio.fx.service;

import com.spendio.reference.service.CurrencyService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.money.ConversionResult;
import com.spendio.sharedkernel.money.FxConversionPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Base-currency conversion (ARCHITECTURE.md D.5): {@code amountMinor} in the source
 * currency's minor units, resolved through {@link FxRateResolver} and rounded
 * HALF_EVEN into the target currency's minor unit (0-decimal JPY, 3-decimal KWD and
 * everything between are handled by the currencies table).
 *
 * <p>Identity conversions return rate 1 and never touch the database.
 */
@Service
public class FxConversionService implements FxConversionPort {

    private final FxRateResolver resolver;
    private final CurrencyService currencies;

    public FxConversionService(FxRateResolver resolver, CurrencyService currencies) {
        this.resolver = resolver;
        this.currencies = currencies;
    }

    @Override
    public ConversionResult convert(long amountMinor, String fromCurrency, String toCurrency, LocalDate onDate) {
        if (fromCurrency == null || toCurrency == null) {
            throw new IllegalArgumentException("currencies are required");
        }
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return new ConversionResult(BigDecimal.ONE, amountMinor, null, onDate, false);
        }
        ResolvedRate resolved = resolver.resolveOrFail(fromCurrency, toCurrency, onDate);
        return new ConversionResult(resolved.rate(), applyRate(amountMinor, fromCurrency, toCurrency, resolved.rate()),
                resolved.rateId(), resolved.rateDate(), resolved.stale());
    }

    @Override
    public ConversionResult convertWithRate(long amountMinor, String fromCurrency, String toCurrency, BigDecimal rate) {
        if (fromCurrency == null || toCurrency == null) {
            throw new IllegalArgumentException("currencies are required");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        long target = applyRate(amountMinor, fromCurrency, toCurrency, rate);
        return new ConversionResult(rate, target, null, null, false);
    }

    private long applyRate(long amountMinor, String fromCurrency, String toCurrency, BigDecimal rate) {
        int fromDigits = minorUnit(fromCurrency);
        int toDigits = minorUnit(toCurrency);
        return BigDecimal.valueOf(amountMinor)
                .multiply(rate)
                .movePointRight(toDigits - fromDigits)
                .setScale(0, RoundingMode.HALF_EVEN)
                .longValueExact();
    }

    private int minorUnit(String currencyCode) {
        try {
            return currencies.get(currencyCode).minorUnit();
        } catch (BusinessException unknownCurrency) {
            // unknown code: assume the common 2-decimal minor unit rather than failing the maths
            return 2;
        }
    }
}