package com.spendio.fx.service;

import com.spendio.fx.domain.FxRate;
import com.spendio.fx.dto.request.ManualRateRequest;
import com.spendio.fx.dto.response.ConvertResponse;
import com.spendio.fx.dto.response.FxRateResponse;
import com.spendio.fx.job.FxRateRefreshJob;
import com.spendio.fx.repository.FxRateRepository;
import com.spendio.reference.service.CurrencyService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.ConversionResult;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FX read/write API behind the REST controller (ARCHITECTURE.md D.5): resolution,
 * manual overrides, rate history and the user-triggered refresh.
 */
@Service
public class FxRateService {

    private final FxRateRepository rates;
    private final FxRateResolver resolver;
    private final FxConversionService conversion;
    private final FxCacheService cache;
    private final FxRateRefreshJob refreshJob;
    private final CurrencyService currencies;

    public FxRateService(
            FxRateRepository rates,
            FxRateResolver resolver,
            FxConversionService conversion,
            FxCacheService cache,
            FxRateRefreshJob refreshJob,
            CurrencyService currencies) {
        this.rates = rates;
        this.resolver = resolver;
        this.conversion = conversion;
        this.cache = cache;
        this.refreshJob = refreshJob;
        this.currencies = currencies;
    }

    /** Resolved rate for the pair as of {@code onDate}; raises FX_RATE_UNAVAILABLE when missing. */
    public FxRateResponse findRate(String from, String to, LocalDate onDate) {
        currencies.requireSupported(from);
        currencies.requireSupported(to);
        ResolvedRate resolved = resolver.resolveOrFail(from, to, onDate);
        return new FxRateResponse(from.toUpperCase(), to.toUpperCase(), resolved.rate(),
                resolved.rateDate(), resolved.source());
    }

    /** Amount in minor units of {@code from}, priced into minor units of {@code to}. */
    public ConvertResponse convert(long amountMinor, String from, String to, LocalDate onDate) {
        currencies.requireSupported(from);
        currencies.requireSupported(to);
        ResolvedRate resolved = resolver.resolveOrFail(from, to, onDate);
        ConversionResult result = conversion.convert(amountMinor, from, to, onDate);
        return new ConvertResponse(amountMinor, result.targetMinor(), result.rate(),
                result.rateDate(), resolved.source(), result.stale());
    }

    /** Manual override: upserted under the {@code manual} provider, cache invalidated. */
    @Transactional
    public FxRateResponse saveManualRate(ManualRateRequest request) {
        currencies.requireSupported(request.baseCurrencyCode());
        currencies.requireSupported(request.quoteCurrencyCode());
        if (request.baseCurrencyCode().equals(request.quoteCurrencyCode())) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY,
                    "A manual rate needs two different currencies");
        }
        FxRate row = rates
                .findByBaseCurrencyCodeAndQuoteCurrencyCodeAndRateDateAndProvider(
                        request.baseCurrencyCode(), request.quoteCurrencyCode(), request.rateDate(),
                        FxRateResolver.MANUAL_PROVIDER)
                .orElseGet(() -> {
                    FxRate fresh = new FxRate();
                    fresh.setBaseCurrencyCode(request.baseCurrencyCode());
                    fresh.setQuoteCurrencyCode(request.quoteCurrencyCode());
                    fresh.setRateDate(request.rateDate());
                    fresh.setProvider(FxRateResolver.MANUAL_PROVIDER);
                    fresh.setManual(true);
                    return fresh;
                });
        row.setRate(request.rate());
        FxRate saved = rates.save(row);
        cache.invalidateAll();
        return new FxRateResponse(saved.getBaseCurrencyCode(), saved.getQuoteCurrencyCode(),
                saved.getRate(), saved.getRateDate(), FxRateResolver.MANUAL_PROVIDER);
    }

    /** Rate history for a pair (optionally bounded by date); newest first. */
    public List<FxRateResponse> history(String base, String quote, LocalDate from, LocalDate to) {
        String b = base.toUpperCase();
        String q = quote.toUpperCase();
        List<FxRate> rows = (from != null && to != null)
                ? rates.findByBaseCurrencyCodeAndQuoteCurrencyCodeAndRateDateBetweenOrderByRateDateDescFetchedAtDesc(b, q, from, to)
                : rates.findByBaseCurrencyCodeAndQuoteCurrencyCodeOrderByRateDateDescFetchedAtDesc(b, q);
        return rows.stream()
                .map(row -> new FxRateResponse(row.getBaseCurrencyCode(), row.getQuoteCurrencyCode(),
                        row.getRate(), row.getRateDate(),
                        row.isManual() ? FxRateResolver.MANUAL_PROVIDER : row.getProvider()))
                .toList();
    }

    /** User-triggered refresh: same code path as the daily scheduler. */
    @Transactional
    public FxRateRefreshJob.RefreshSummary refresh() {
        return refreshJob.run();
    }
}