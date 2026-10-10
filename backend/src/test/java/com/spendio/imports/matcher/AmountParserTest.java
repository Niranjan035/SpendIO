package com.spendio.imports.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Money-cell parsing (ARCHITECTURE.md D.12 step 4): every shape a bank CSV throws. */
class AmountParserTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // plain decimals
            "'1234.56', 123456",
            "'1234', 123400",
            "'1234.5', 123450",
            "'0.99', 99",
            "'.99', 99",
            "'1.234,567', 123457",
            // signs
            "'-1234.56', -123456",
            "'+1234.56', 123456",
            "'(1234.56)', -123456",
            "'($1,234.56)', -123456",
            "'-$1,234.56', -123456",
            // grouping - dot decimal (US)
            "'1,234.56', 123456",
            "'12,345,678.90', 1234567890",
            // grouping - comma decimal (EU)
            "'1.234,56', 123456",
            "'1.234.567,89', 123456789",
            "'1234,56', 123456",
            "'1.234', 123400",
            // currencies
            "'$1234.56', 123456",
            "'€1234.56', 123456",
            "'£1234.56', 123456",
            "'1234.56 EUR', 123456",
            "'1234.56 USD', 123456",
            // CR/DR suffixes
            "'1234.56 CR', 123456",
            "'1234.56 DR', -123456",
            "'DR 1234.56', -123456",
            "'CR 1234.56', 123456",
            // spaces
            "'1 234,56', 123456",
            "'  1234.56  ', 123456",
            // quoted cell
            "'\"1234.56\"', 123456"
    })
    void parsesBankShapes(String raw, long expected) {
        assertThat(AmountParser.parseMinor(raw)).hasValue(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-number", "€€", "-", "(", "$", "CR"})
    void blankOrGarbageIsEmpty(String raw) {
        assertThat(AmountParser.parseMinor(raw)).isEmpty();
    }

    @Test
    void nullIsEmpty() {
        assertThat(AmountParser.parseMinor(null)).isEmpty();
    }

    @Test
    void respectsCustomFractionDigits() {
        assertThat(AmountParser.parseMinor("1.234,567", 3)).hasValue(1234567);
        assertThat(AmountParser.parseMinor("1234.5", 0)).hasValue(1235);
        // JPY-style: no decimals at all
        assertThat(AmountParser.parseMinor("1234", 0)).hasValue(1234);
    }

    @Test
    void roundsHalfUpAtMinorPrecision() {
        assertThat(AmountParser.parseMinor("1.234,565")).hasValue(123457);
        assertThat(AmountParser.parseMinor("1.234,564")).hasValue(123456);
    }

    @Test
    void parenNegativeWinsOverCurrencySymbol() {
        assertThat(AmountParser.parseMinor("(€1.234,56)")).hasValue(-123456);
    }
}