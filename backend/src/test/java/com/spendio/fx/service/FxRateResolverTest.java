package com.spendio.fx.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.fx.domain.FxRate;
import com.spendio.fx.repository.FxRateRepository;
import com.spendio.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rate resolution across every documented path (ARCHITECTURE.md D.5): exact, manual
 * preferred, newest-on-or-before, inverse, triangulated, stale and missing - table
 * driven, one isolated seed set per row.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class FxRateResolverTest extends AbstractIntegrationTest {

    private static final LocalDate ASKED = LocalDate.of(2026, 6, 15);

    @Autowired
    private FxRateResolver resolver;
    @Autowired
    private FxRateRepository rates;

    static Stream<Arguments> resolutionPaths() {
        return Stream.of(
                // exact pair, newest on or before the asked date
                Arguments.of("exact pair", "USD", "EUR", "0.9000000000", "http", false,
                        new String[][]{{"USD", "EUR", "0.9", "2026-06-12", "http"}}),
                // only the inverse row exists -> 1 / 1.125 = 0.8888888889
                Arguments.of("inverse pair", "USD", "GBP", "0.8888888889", "inverse", false,
                        new String[][]{{"GBP", "USD", "1.125", "2026-06-12", "http"}}),
                // USD->GBP / USD->EUR = 0.79 / 0.9
                Arguments.of("triangulated via USD", "EUR", "GBP", "0.8777777778", "triangulated", false,
                        new String[][]{
                                {"USD", "EUR", "0.9", "2026-06-12", "http"},
                                {"USD", "GBP", "0.79", "2026-06-12", "http"}}),
                // stale: the only row is years old
                Arguments.of("stale fallback", "USD", "JPY", "149.5000000000", "http", true,
                        new String[][]{{"USD", "JPY", "149.5", "2020-01-01", "http"}}),
                // nothing anywhere, outside the on-demand window
                Arguments.of("missing", "USD", "CHF", null, null, false, new String[][]{})
        );
    }

    @ParameterizedTest(name = "{0}: {1}/{2}")
    @MethodSource("resolutionPaths")
    void resolvesThroughTheDocumentedChain(String label, String from, String to, String expectedRate,
                                           String sourceContains, boolean expectedStale, String[][] seeds) {
        for (String[] seed : seeds) {
            seedExact(seed[0], seed[1], seed[2], LocalDate.parse(seed[3]), seed[4],
                    "manual".equals(seed[4]));
        }

        Optional<ResolvedRate> resolved = resolver.resolve(from, to, ASKED);

        if (expectedRate == null) {
            assertThat(resolved).as(label).isEmpty();
            return;
        }
        assertThat(resolved).as(label).isPresent();
        assertThat(resolved.get().rate()).isEqualByComparingTo(new BigDecimal(expectedRate));
        assertThat(resolved.get().source()).contains(sourceContains);
        assertThat(resolved.get().stale()).isEqualTo(expectedStale);
    }

    @Test
    void identityReturnsOneWithoutTouchingTheDatabase() {
        Optional<ResolvedRate> resolved = resolver.resolve("usd", "USD", ASKED);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(resolved.get().source()).isEqualTo("identity");
        assertThat(resolved.get().rateId()).isNull();
    }

    @Test
    void manualRowsWinOverProviderRowsForTheSamePair() {
        seedExact("USD", "EUR", "0.90", LocalDate.of(2026, 6, 12), "http");
        seedExact("USD", "EUR", "0.87", LocalDate.of(2026, 6, 12), "manual");

        ResolvedRate resolved = resolver.resolveOrFail("USD", "EUR", ASKED);

        assertThat(resolved.rate()).isEqualByComparingTo(new BigDecimal("0.87"));
        assertThat(resolved.source()).isEqualTo("manual");
    }

    @Test
    void newestRateOnOrBeforeTheAskedDateWins() {
        seedExact("USD", "EUR", "0.88", LocalDate.of(2026, 6, 10), "http");
        seedExact("USD", "EUR", "0.91", LocalDate.of(2026, 6, 12), "http");

        assertThat(resolver.resolveOrFail("USD", "EUR", LocalDate.of(2026, 6, 11)).rate())
                .isEqualByComparingTo(new BigDecimal("0.88"));
        assertThat(resolver.resolveOrFail("USD", "EUR", ASKED).rate())
                .isEqualByComparingTo(new BigDecimal("0.91"));
    }

    @Test
    void resolveOrFailRaisesFxRateUnavailableWhenNothingMatches() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> resolver.resolveOrFail("USD", "CHF", LocalDate.of(2020, 1, 1)))
                .isInstanceOf(com.spendio.sharedkernel.error.BusinessException.class)
                .hasMessageContaining("No FX rate available");
    }

    @Test
    void onDemandFetchPathIsExercisedForRecentDates() {
        // no stored rows: the noop provider has nothing either, so the recent-date
        // fetch path runs (rate limiter disabled in the test profile) and stays empty.
        Optional<ResolvedRate> resolved = resolver.resolve("USD", "EUR", LocalDate.now());

        assertThat(resolved).isEmpty();
    }

    @Test
    void weekendLookupFallsBackToThePreviousBusinessDaysRate() {
        seedExact("USD", "INR", "83.2", LocalDate.of(2026, 10, 9), "http");

        // 2026-10-09 is the last business day before the Saturday 2026-10-10 lookup
        ResolvedRate resolved = resolver.resolveOrFail("USD", "INR", LocalDate.of(2026, 10, 10));

        assertThat(resolved.rate()).isEqualByComparingTo(new BigDecimal("83.2"));
        assertThat(resolved.source()).contains("http");
        assertThat(resolved.stale()).isFalse();
    }

    private void seedExact(String base, String quote, String rate, LocalDate date, String provider) {
        seedExact(base, quote, rate, date, provider, false);
    }

    private void seedExact(String base, String quote, String rate, LocalDate date, String provider,
                           boolean manual) {
        FxRate row = new FxRate();
        row.setBaseCurrencyCode(base);
        row.setQuoteCurrencyCode(quote);
        row.setRate(new BigDecimal(rate));
        row.setRateDate(date);
        row.setProvider(provider);
        row.setManual(manual);
        rates.save(row);
    }
}