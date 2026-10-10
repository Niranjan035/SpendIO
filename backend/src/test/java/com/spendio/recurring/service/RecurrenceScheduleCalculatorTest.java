package com.spendio.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.sharedkernel.domain.enums.RecurrenceFrequency;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Occurrence arithmetic (ARCHITECTURE.md D.9, J.6) driven by the table in
 * {@code fixtures/recurring-cases.json}: anchored math per frequency, clamping that
 * never drifts, the end-date bound, and the weekend shift that lands before a window
 * rejecting its raw date instead of double counting it.
 */
class RecurrenceScheduleCalculatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RecurrenceScheduleCalculator calculator = new RecurrenceScheduleCalculator();

    static Stream<Case> fixture() throws IOException {
        try (InputStream in = RecurrenceScheduleCalculatorTest.class
                .getResourceAsStream("/fixtures/recurring-cases.json")) {
            assertThat(in).as("fixtures/recurring-cases.json on the test classpath").isNotNull();
            return Arrays.stream(JSON.readValue(in, Case[].class));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixture")
    void previewMatchesTheFixtureTable(Case c) {
        RecurringTransaction rule = c.rule();
        List<LocalDate> expected = c.expectedDates();

        assertThat(calculator.firstOnOrAfter(rule, c.from()))
                .isEqualTo(expected.isEmpty() ? null : expected.get(0));
        assertThat(calculator.preview(rule, c.from(), Math.max(expected.size(), 3))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixture")
    void nextIsTheFollowingOccurrence(Case c) {
        RecurringTransaction rule = c.rule();
        List<LocalDate> expected = c.expectedDates();
        if (expected.isEmpty()) {
            assertThat(calculator.next(rule, c.from())).isNull();
            return;
        }
        LocalDate next = calculator.next(rule, c.from());
        if (expected.get(0).equals(c.from())) {
            if (expected.size() > 1) {
                assertThat(next).isEqualTo(expected.get(1));
            }
        } else {
            assertThat(next).isEqualTo(expected.get(0));
        }
    }

    @Test
    void fixtureCoversEveryFrequency() throws IOException {
        List<String> frequencies = fixture().map(c -> c.frequency.name()).distinct().sorted().toList();
        assertThat(frequencies).containsExactly("CUSTOM", "DAILY", "MONTHLY", "WEEKLY", "YEARLY");
    }

    @Test
    void nextIsStrictlyAfterTheFromDateWhileFirstIsOnIt() {
        RecurringTransaction rule = Case.of(RecurrenceFrequency.DAILY, "2026-06-01", null, null, 1,
                null, null, false).rule();
        assertThat(calculator.firstOnOrAfter(rule, LocalDate.of(2026, 6, 1)))
                .isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(calculator.next(rule, LocalDate.of(2026, 6, 1)))
                .isEqualTo(LocalDate.of(2026, 6, 2));
    }

    /** One row of the JSON table (also serves the two focused tests above). */
    record Case(
            String name,
            RecurrenceFrequency frequency,
            String startDate,
            String endDate,
            String targetOccurrenceDate,
            Integer intervalCount,
            Integer dayOfMonth,
            Integer dayOfWeek,
            Boolean skipWeekends,
            LocalDate from,
            List<LocalDate> expected) {

        static Case of(RecurrenceFrequency frequency, String startDate, String endDate, String target,
                int intervalCount, Integer dayOfMonth, Integer dayOfWeek, boolean skipWeekends) {
            return new Case("focused", frequency, startDate, endDate, target, intervalCount, dayOfMonth,
                    dayOfWeek, skipWeekends, LocalDate.parse(startDate), List.of());
        }

        @Override
        public String toString() {
            return name;
        }

        List<LocalDate> expectedDates() {
            return expected == null ? List.of() : expected;
        }

        RecurringTransaction rule() {
            short interval = (short) (intervalCount == null ? 1 : intervalCount);
            return RecurringTransaction.create(
                    1L, null, null, null,
                    TransactionType.EXPENSE,
                    TransactionDirection.OUTFLOW,
                    1_000L, "USD",
                    BigDecimal.ONE, 1_000L,
                    null, null,
                    frequency,
                    interval,
                    dayOfMonth,
                    dayOfWeek,
                    Boolean.TRUE.equals(skipWeekends),
                    LocalDate.parse(startDate),
                    endDate == null ? null : LocalDate.parse(endDate),
                    LocalDate.parse(startDate),
                    true, (short) 3,
                    targetOccurrenceDate == null ? null : LocalDate.parse(targetOccurrenceDate));
        }
    }
}
