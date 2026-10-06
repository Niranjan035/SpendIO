package com.spendio.sharedkernel.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Inclusive business-date range (ISO-8601 {@link LocalDate}, ARCHITECTURE.md E.1).
 * Used by budgets, analytics and recurring ranges - never by timestamps.
 */
public record DateRange(LocalDate startInclusive, LocalDate endInclusive) {

    public DateRange {
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endInclusive, "endInclusive must not be null");
        if (startInclusive.isAfter(endInclusive)) {
            throw new IllegalArgumentException(
                    "start " + startInclusive + " is after end " + endInclusive);
        }
    }

    public static DateRange of(LocalDate startInclusive, LocalDate endInclusive) {
        return new DateRange(startInclusive, endInclusive);
    }

    public static DateRange day(LocalDate date) {
        return new DateRange(date, date);
    }

    public static DateRange month(LocalDate anyDateInMonth) {
        YearMonth month = YearMonth.from(anyDateInMonth);
        return new DateRange(month.atDay(1), month.atEndOfMonth());
    }

    public static DateRange month(YearMonth month) {
        return new DateRange(month.atDay(1), month.atEndOfMonth());
    }

    public static DateRange year(int year) {
        return new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startInclusive) && !date.isAfter(endInclusive);
    }

    public boolean overlaps(DateRange other) {
        return !startInclusive.isAfter(other.endInclusive) && !other.startInclusive.isAfter(endInclusive);
    }

    /** Number of calendar days covered, inclusive. */
    public long days() {
        return ChronoUnit.DAYS.between(startInclusive, endInclusive) + 1;
    }
}
