package com.spendio.imports.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Date-cell parsing (ARCHITECTURE.md D.12 step 4): hint-first, then candidates. */
class DateParserTest {

    private static final LocalDate D = LocalDate.of(2026, 3, 9);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "2026-03-09",
            "09/03/2026",
            "09-03-2026",
            "09.03.2026",
            "09 Mar 2026",
            "9 Mar 2026",
            "09 March 2026",
            "2026/03/09",
            "09/03/26"
    })
    void parsesDayFirstCandidates(String raw) {
        assertThat(DateParser.tryParse(raw, null)).contains(D);
    }

    @Test
    void hintWinsOverCandidateOrder() {
        // 03/04/2026 is ambiguous: month-first under a US hint, day-first otherwise
        assertThat(DateParser.tryParse("03/04/2026", "MM/dd/yyyy")).contains(LocalDate.of(2026, 3, 4));
        assertThat(DateParser.tryParse("03/04/2026", "dd/MM/yyyy")).contains(LocalDate.of(2026, 4, 3));
        assertThat(DateParser.tryParse("03/04/2026", null)).contains(LocalDate.of(2026, 4, 3));
    }

    @Test
    void parsesIsoWithTime() {
        assertThat(DateParser.tryParse("2026-03-09T14:30", null)).contains(D);
        assertThat(DateParser.tryParse("2026-03-09T14:30:00", null)).contains(D);
    }

    @Test
    void parsesEpochSecondsAndMillis() {
        assertThat(DateParser.tryParse("1773043200", null)).contains(LocalDate.of(2026, 3, 9));
        assertThat(DateParser.tryParse("1773043200000", null)).contains(LocalDate.of(2026, 3, 9));
    }

    @Test
    void parsesRfc1123BankExport() {
        assertThat(DateParser.tryParse("Mon, 09 Mar 2026 00:00:00 GMT", null)).contains(D);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not a date", "32/13/2026", "2026-13-45", "abc"})
    void unparseableIsEmpty(String raw) {
        assertThat(DateParser.tryParse(raw, null)).isEmpty();
    }

    @Test
    void nullIsEmpty() {
        assertThat(DateParser.tryParse(null, null)).isEmpty();
    }

    @Test
    void quotedCellIsUnwrapped() {
        assertThat(DateParser.tryParse("\"2026-03-09\"", null)).contains(D);
    }

    @Test
    void wrongHintFallsBackToCandidates() {
        assertThat(DateParser.tryParse("2026-03-09", "dd/MM/yyyy")).contains(D);
    }
}