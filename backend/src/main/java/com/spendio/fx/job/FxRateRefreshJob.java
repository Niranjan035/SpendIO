package com.spendio.fx.job;

import com.spendio.fx.domain.FxRate;
import com.spendio.fx.repository.FxRateRepository;
import com.spendio.fx.service.FxCacheService;
import com.spendio.fx.service.FxRateResolver;
import com.spendio.fx.support.FxProviderClient;
import com.spendio.reference.dto.CurrencyResponse;
import com.spendio.reference.service.CurrencyService;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily FX refresh (ARCHITECTURE.md D.5): fetches every active currency against the
 * pivot, upserts the provider's own rows and never touches manual overrides (they live
 * under the {@code manual} provider and win every resolution). Each run is written to
 * {@code job_execution_log} and invalidates the rate cache.
 */
@Component
public class FxRateRefreshJob {

    public static final String JOB_NAME = "fx-rate-refresh";

    private static final Logger log = LoggerFactory.getLogger(FxRateRefreshJob.class);

    private final FxRateRepository rates;
    private final FxProviderClient provider;
    private final FxCacheService cache;
    private final CurrencyService currencies;
    private final JobExecutionLogRepository jobLog;
    private final String pivotCurrency;

    public FxRateRefreshJob(
            FxRateRepository rates,
            FxProviderClient provider,
            FxCacheService cache,
            CurrencyService currencies,
            JobExecutionLogRepository jobLog,
            @Value("${spendio.fx.pivot-currency:USD}") String pivotCurrency) {
        this.rates = rates;
        this.provider = provider;
        this.cache = cache;
        this.currencies = currencies;
        this.jobLog = jobLog;
        this.pivotCurrency = pivotCurrency.toUpperCase();
    }

    @Scheduled(cron = "${spendio.fx.refresh-cron:0 15 3 * * *}")
    @Transactional
    public RefreshSummary scheduledRun() {
        return run();
    }

    /** Manual/test trigger for {@code POST /api/v1/fx/refresh}. */
    @Transactional
    public RefreshSummary run() {
        LocalDate today = LocalDate.now();
        JobExecutionLog execution = JobExecutionLog.start(JOB_NAME, Instant.now());
        int written = 0;
        try {
            List<CurrencyResponse> active = currencies.listActive();
            Map<String, BigDecimal> fetched = provider.fetch(pivotCurrency, today);
            for (CurrencyResponse currency : active) {
                if (currency.code().equalsIgnoreCase(pivotCurrency)) {
                    continue;
                }
                BigDecimal rate = fetched.get(currency.code().toUpperCase());
                if (rate == null || rate.signum() <= 0) {
                    continue;
                }
                written += upsert(pivotCurrency, currency.code().toUpperCase(), rate, today) ? 1 : 0;
            }
            execution.succeed(written, provider.name() + " rows for " + today, Instant.now());
        } catch (RuntimeException failure) {
            execution.fail(failure.getMessage(), Instant.now());
            log.warn("fx refresh failed: {}", failure.getMessage());
        } finally {
            jobLog.save(execution);
            cache.invalidateAll();
        }
        return new RefreshSummary(JOB_NAME, today, provider.name(), written, execution.getStatus());
    }

    /** Provider rows only: an existing manual row for the pair/date is a different row. */
    private boolean upsert(String base, String quote, BigDecimal rate, LocalDate date) {
        if (FxRateResolver.MANUAL_PROVIDER.equalsIgnoreCase(provider.name())) {
            return false;
        }
        FxRate row = rates
                .findByBaseCurrencyCodeAndQuoteCurrencyCodeAndRateDateAndProvider(base, quote, date, provider.name())
                .orElseGet(() -> {
                    FxRate fresh = new FxRate();
                    fresh.setBaseCurrencyCode(base);
                    fresh.setQuoteCurrencyCode(quote);
                    fresh.setRateDate(date);
                    fresh.setProvider(provider.name());
                    fresh.setManual(false);
                    return fresh;
                });
        row.setRate(rate);
        rates.save(row);
        return true;
    }

    public record RefreshSummary(String job, LocalDate rateDate, String provider, int rows, String status) {
    }
}