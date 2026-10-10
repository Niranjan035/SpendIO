package com.spendio.fx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendio.fx.domain.FxRate;
import com.spendio.fx.repository.FxRateRepository;
import com.spendio.sharedkernel.money.ConversionResult;
import com.spendio.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversion maths (ARCHITECTURE.md D.5): identity never touches the database, and
 * amounts round HALF_EVEN into the target currency's minor unit - 2-decimal USD,
 * 0-decimal JPY and 3-decimal KWD.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class FxConversionServiceTest extends AbstractIntegrationTest {

    private static final LocalDate ASKED = LocalDate.of(2026, 10, 2);

    @Autowired
    private FxConversionService conversion;
    @Autowired
    private FxRateRepository rates;

    @Test
    void identityReturnsTheSameAmountWithRateOneAndNoDatabaseHit() {
        ConversionResult result = conversion.convert(123_45, "USD", "USD", ASKED);

        assertThat(result.targetMinor()).isEqualTo(123_45);
        assertThat(result.rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.rateId()).isNull();
        assertThat(result.stale()).isFalse();
    }

    @Test
    void convertsIntoZeroDecimalJpyWithHalfEvenRounding() {
        seed("USD", "JPY", "150", LocalDate.of(2026, 10, 1));

        // 7.00 USD * 150 = 1050 JPY exactly
        assertThat(conversion.convert(700, "USD", "JPY", ASKED).targetMinor()).isEqualTo(1_050);
        // 1.23 USD * 150 = 184.5 JPY -> HALF_EVEN -> 184
        assertThat(conversion.convert(123, "USD", "JPY", ASKED).targetMinor()).isEqualTo(184);
    }

    @Test
    void convertsIntoThreeDecimalKwd() {
        seed("USD", "KWD", "0.3068", LocalDate.of(2026, 10, 1));

        // 10.00 USD * 0.3068 = 3.068 KWD = 3068 fils
        assertThat(conversion.convert(1_000, "USD", "KWD", ASKED).targetMinor()).isEqualTo(3_068);
        // 9.99 USD * 0.3068 = 3.0649... KWD = 3064.932 fils -> 3065
        assertThat(conversion.convert(999, "USD", "KWD", ASKED).targetMinor()).isEqualTo(3_065);
    }

    @Test
    void convertsBackIntoTwoDecimalUsd() {
        seed("USD", "EUR", "0.5", LocalDate.of(2026, 10, 1));

        // 100.00 EUR * (1 / 0.5) = 200.00 USD = 20000 cents
        assertThat(conversion.convert(10_000, "EUR", "USD", ASKED).targetMinor()).isEqualTo(20_000);
    }

    @Test
    void staleRateIsFlaggedOnTheResult() {
        seed("USD", "JPY", "149.5", LocalDate.of(2020, 1, 1));

        ConversionResult result = conversion.convert(1_000, "USD", "JPY", LocalDate.of(2026, 6, 15));

        assertThat(result.stale()).isTrue();
        assertThat(result.rateId()).isNotNull();
    }

    @Test
    void convertWithRateAppliesTheGivenRateWithoutTheDatabase() {
        ConversionResult result = conversion.convertWithRate(10_000, "USD", "EUR", new BigDecimal("0.5"));

        assertThat(result.targetMinor()).isEqualTo(5_000);
        assertThat(result.rate()).isEqualByComparingTo(new BigDecimal("0.5"));
        assertThat(result.rateId()).isNull();
        assertThat(result.stale()).isFalse();
    }

    @Test
    void convertWithRateRefusesANonPositiveRate() {
        assertThatThrownBy(() -> conversion.convertWithRate(10_000, "USD", "EUR", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conversion.convertWithRate(10_000, "USD", "EUR", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void seed(String base, String quote, String rate, LocalDate date) {
        FxRate row = new FxRate();
        row.setBaseCurrencyCode(base);
        row.setQuoteCurrencyCode(quote);
        row.setRate(new BigDecimal(rate));
        row.setRateDate(date);
        row.setProvider("manual");
        row.setManual(true);
        rates.save(row);
    }
}