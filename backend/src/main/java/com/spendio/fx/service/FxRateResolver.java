package com.spendio.fx.service;

import com.spendio.fx.domain.FxRate;
import com.spendio.fx.repository.FxRateRepository;
import com.spendio.fx.support.FxProviderClient;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rate resolution with the documented fallback chain (ARCHITECTURE.md D.5):
 *
 * <ol>
 *   <li>exact pair, rate_date &lt;= onDate and within the freshness window
 *       ({@code spendio.fx.freshness-days}, default 7), newest first, manual rows preferred;</li>
 *   <li>inverse pair - {@code 1 / rate};</li>
 *   <li>triangulation through the pivot currency (default USD);</li>
 *   <li>on-demand provider fetch when the date is recent, rate limited and cached;</li>
 *   <li>stale fallback to the most recent known rate ({@code stale = true});</li>
 *   <li>otherwise: missing.</li>
 * </ol>
 *
 * <p>Identity conversions short-circuit before any lookup and never touch the cache or
 * the database.
 */
@Service
public class FxRateResolver {

    public static final String MANUAL_PROVIDER = "manual";
    static final String IDENTITY_SOURCE = "identity";

    private final FxRateRepository rates;
    private final FxCacheService cache;
    private final FxProviderClient provider;
    private final String pivotCurrency;
    private final int onDemandMaxDays;
    private final int freshnessDays;

    public FxRateResolver(
            FxRateRepository rates,
            FxCacheService cache,
            FxProviderClient provider,
            @Value("${spendio.fx.pivot-currency:USD}") String pivotCurrency,
            @Value("${spendio.fx.on-demand-max-days:7}") int onDemandMaxDays,
            @Value("${spendio.fx.freshness-days:7}") int freshnessDays) {
        this.rates = rates;
        this.cache = cache;
        this.provider = provider;
        this.pivotCurrency = pivotCurrency.toUpperCase();
        this.onDemandMaxDays = onDemandMaxDays;
        this.freshnessDays = freshnessDays;
    }

    /** Resolution without throwing: empty when no rate exists by any path. */
    @Transactional
    public Optional<ResolvedRate> resolve(String from, String to, LocalDate onDate) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("currencies are required");
        }
        onDate = onDate == null ? LocalDate.now() : onDate;
        from = from.toUpperCase();
        to = to.toUpperCase();
        if (from.equals(to)) {
            return Optional.of(ResolvedRate.of(BigDecimal.ONE, null, onDate, IDENTITY_SOURCE));
        }
        String key = FxCacheService.key(from, to, onDate);
        Optional<ResolvedRate> cached = cache.get(key);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ResolvedRate> resolved = compute(from, to, onDate);
        cache.put(key, resolved);
        return resolved;
    }

    /** Same as {@link #resolve} but raises {@code FX_RATE_UNAVAILABLE} when missing. */
    public ResolvedRate resolveOrFail(String from, String to, LocalDate onDate) {
        return resolve(from, to, onDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.FX_RATE_UNAVAILABLE,
                        "No FX rate available for " + from + "/" + to + " on " + onDate)
                        .withProperty("fromCurrency", from)
                        .withProperty("toCurrency", to));
    }

    private Optional<ResolvedRate> compute(String from, String to, LocalDate onDate) {
        // 1. exact pair
        Optional<ResolvedRate> exact = exact(from, to, onDate);
        if (exact.isPresent()) {
            return exact;
        }
        // 2. inverse pair
        Optional<ResolvedRate> inverse = exact(to, from, onDate);
        if (inverse.isPresent()) {
            return inverse.map(r -> ResolvedRate.of(invert(r.rate()), r.rateId(), r.rateDate(),
                    "inverse(" + r.source() + ")"));
        }
        // 3. triangulated via the pivot currency
        Optional<ResolvedRate> tri = triangulate(from, to, onDate);
        if (tri.isPresent()) {
            return tri;
        }
        // 4. on-demand fetch for recent dates (rate limited)
        Optional<ResolvedRate> fetched = fetchOnDemand(from, to, onDate);
        if (fetched.isPresent()) {
            return fetched;
        }
        // 5. stale fallback: most recent known rate for the pair, either direction
        return stale(from, to, onDate);
    }

    private Optional<ResolvedRate> exact(String from, String to, LocalDate onDate) {
        return rates.findLatestOnOrBefore(from, to, onDate).stream()
                .findFirst()
                .filter(row -> !row.getRateDate().plusDays(freshnessDays).isBefore(onDate))
                .map(row -> ResolvedRate.of(row.getRate(), row.getId(), row.getRateDate(), sourceOf(row)));
    }

    private static String sourceOf(FxRate row) {
        return row.isManual() ? MANUAL_PROVIDER : row.getProvider();
    }

    private static BigDecimal invert(BigDecimal rate) {
        return BigDecimal.ONE.divide(rate, Math.max(10, rate.scale()), RoundingMode.HALF_EVEN);
    }

    private Optional<ResolvedRate> triangulate(String from, String to, LocalDate onDate) {
        if (from.equals(pivotCurrency) || to.equals(pivotCurrency)) {
            return Optional.empty();
        }
        // pivot -> to (direct row) and pivot -> from (direct row, inverted if needed)
        Optional<ResolvedRate> pivotTo = exact(pivotCurrency, to, onDate);
        if (pivotTo.isEmpty()) {
            return Optional.empty();
        }
        Optional<ResolvedRate> pivotFrom = exact(pivotCurrency, from, onDate);
        if (pivotFrom.isPresent()) {
            BigDecimal rate = pivotTo.get().rate().divide(pivotFrom.get().rate(), 10, RoundingMode.HALF_EVEN);
            return Optional.of(ResolvedRate.of(rate, null, pivotTo.get().rateDate(), "triangulated"));
        }
        Optional<ResolvedRate> fromToPivot = exact(from, pivotCurrency, onDate);
        if (fromToPivot.isPresent()) {
            BigDecimal rate = fromToPivot.get().rate().multiply(pivotTo.get().rate());
            return Optional.of(ResolvedRate.of(rate, null, pivotTo.get().rateDate(), "triangulated"));
        }
        return Optional.empty();
    }

    private Optional<ResolvedRate> fetchOnDemand(String from, String to, LocalDate onDate) {
        LocalDate today = LocalDate.now();
        boolean recent = !onDate.isBefore(today.minusDays(onDemandMaxDays));
        if (!recent || !cache.tryReserveFetch()) {
            return Optional.empty();
        }
        return provider.fetch(from, onDate).entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(to))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .filter(rate -> rate != null && rate.signum() > 0)
                .map(rate -> {
                    FxRate row = persist(from, to, rate, onDate);
                    return ResolvedRate.of(rate, row.getId(), row.getRateDate(),
                            provider.name() + "+fetch");
                });
    }

    private FxRate persist(String from, String to, BigDecimal rate, LocalDate onDate) {
        FxRate row = rates
                .findByBaseCurrencyCodeAndQuoteCurrencyCodeAndRateDateAndProvider(from, to, onDate, provider.name())
                .orElseGet(() -> {
                    FxRate fresh = new FxRate();
                    fresh.setBaseCurrencyCode(from);
                    fresh.setQuoteCurrencyCode(to);
                    fresh.setRateDate(onDate);
                    fresh.setProvider(provider.name());
                    fresh.setManual(false);
                    return fresh;
                });
        row.setRate(rate);
        return rates.save(row);
    }

    private Optional<ResolvedRate> stale(String from, String to, LocalDate onDate) {
        Optional<ResolvedRate> direct = rates.findLatest(from, to).stream()
                .findFirst()
                .map(row -> ResolvedRate.stale(row.getRate(), row.getId(), row.getRateDate(), sourceOf(row)));
        if (direct.isPresent()) {
            return direct;
        }
        return rates.findLatest(to, from).stream()
                .findFirst()
                .map(row -> ResolvedRate.stale(invert(row.getRate()), row.getId(), row.getRateDate(),
                        "inverse(" + sourceOf(row) + ")"));
    }
}