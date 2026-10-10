package com.spendio.fx.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.fx.domain.FxRate;
import com.spendio.fx.repository.FxRateRepository;
import com.spendio.fx.service.FxRateResolver;
import com.spendio.fx.support.FxProviderClient;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import com.spendio.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily refresh behaviour (ARCHITECTURE.md D.5): idempotent upsert, manual rows
 * preserved, cache invalidated and a job_execution_log row per run.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class FxRateRefreshJobTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class StubProviderConfig {
        @Bean
        @Primary
        FxProviderClient stubFxProvider() {
            return new FxProviderClient() {
                @Override
                public String name() {
                    return "stub";
                }

                @Override
                public Map<String, BigDecimal> fetch(String base, LocalDate date) {
                    if (!"USD".equals(base)) {
                        return Map.of();
                    }
                    return Map.of(
                            "EUR", new BigDecimal("0.92"),
                            "GBP", new BigDecimal("0.79"));
                }
            };
        }
    }

    @Autowired
    private FxRateRefreshJob job;
    @Autowired
    private FxRateRepository rates;
    @Autowired
    private FxRateResolver resolver;
    @Autowired
    private JobExecutionLogRepository jobLog;

    @Test
    void refreshWritesProviderRowsAndIsIdempotent() {
        FxRateRefreshJob.RefreshSummary first = job.run();
        FxRateRefreshJob.RefreshSummary second = job.run();

        assertThat(first.rows()).isEqualTo(2);
        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(second.rows()).isEqualTo(2);

        LocalDate today = LocalDate.now();
        assertThat(rates.findByBaseCurrencyCodeAndQuoteCurrencyCodeAndRateDateAndProvider(
                        "USD", "EUR", today, "stub")).isPresent();
        assertThat(rates.findByBaseCurrencyCodeAndQuoteCurrencyCodeAndRateDateAndProvider(
                        "USD", "GBP", today, "stub")).isPresent();
        // still exactly one provider row per pair after two runs
        assertThat(rates.count()).isEqualTo(2);
    }

    @Test
    void manualRatesSurviveTheRefreshAndKeepWinningResolution() {
        FxRate manual = new FxRate();
        manual.setBaseCurrencyCode("USD");
        manual.setQuoteCurrencyCode("EUR");
        manual.setRate(new BigDecimal("0.5"));
        manual.setRateDate(LocalDate.now());
        manual.setProvider(FxRateResolver.MANUAL_PROVIDER);
        manual.setManual(true);
        rates.save(manual);

        job.run();

        // the manual row is untouched
        assertThat(rates.findByBaseCurrencyCodeAndQuoteCurrencyCodeAndRateDateAndProvider(
                        "USD", "EUR", LocalDate.now(), FxRateResolver.MANUAL_PROVIDER))
                .hasValueSatisfying(row -> assertThat(row.getRate()).isEqualByComparingTo("0.5"));
        // and still wins over the freshly fetched provider row
        assertThat(resolver.resolveOrFail("USD", "EUR", LocalDate.now()).source()).isEqualTo("manual");
    }

    @Test
    void everyRunIsLogged() {
        job.run();

        JobExecutionLog execution = jobLog.findFirstByJobNameOrderByStartedAtDesc(FxRateRefreshJob.JOB_NAME)
                .orElseThrow();
        assertThat(execution.getStatus()).isEqualTo("SUCCESS");
        assertThat(execution.getAffectedRows()).isEqualTo(2);
        assertThat(execution.getFinishedAt()).isNotNull();
    }
}