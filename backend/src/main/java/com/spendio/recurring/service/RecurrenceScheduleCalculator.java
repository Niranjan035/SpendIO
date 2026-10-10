package com.spendio.recurring.service;

import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.sharedkernel.domain.PeriodCalculator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Occurrence arithmetic for recurring rules (ARCHITECTURE.md D.9) - pure date maths,
 * no persistence and no status checks: whether a paused rule <em>advances</em> is the
 * claim query's job ({@code status='ACTIVE'}), not the calendar's.
 *
 * <p>Each frequency is an anchor plus an occurrence index {@code k}:
 * <ul>
 *   <li>DAILY - {@code startDate + k*interval} days</li>
 *   <li>WEEKLY - the {@code dayOfWeek} (or {@code startDate}'s) of {@code startDate}'s
 *       week, plus {@code k*7*interval} days</li>
 *   <li>MONTHLY - month {@code startMonth + k*interval}, day clamped to the month's
 *       length (Jan 31 -&gt; Feb 28/29, never March)</li>
 *   <li>YEARLY - {@code addYearsClamped(startDate, k*interval)} (Feb 29 -&gt; Feb 28)</li>
 *   <li>CUSTOM - {@code targetOccurrenceDate + k*interval} days</li>
 * </ul>
 * Anchors are always the original dates, so clamping cannot drift. {@code skipWeekends}
 * shifts Sat -&gt; preceding Fri and Sun -&gt; following Mon after the raw date is found;
 * a shift that lands before the requested window rejects that raw date and tries the
 * next one (so a daily weekend-skipping rule yields Fri, Mon, Tue, ... exactly once).
 */
@Component
public class RecurrenceScheduleCalculator {

    /** Defensive bound for the month/year scan; correct lower bounds keep scans at ~1. */
    private static final int MAX_STEPS = 10_000;

    /** First occurrence on or after {@code from}, or {@code null} past {@code end_date}. */
    public LocalDate firstOnOrAfter(RecurringTransaction rule, LocalDate from) {
        long k = lowerBound(rule, from);
        LocalDate candidate = occurrence(rule, k);
        int steps = 0;
        while ((candidate.isBefore(from) || candidate.isBefore(rule.getStartDate()))
                && steps++ < MAX_STEPS) {
            candidate = occurrence(rule, ++k);
        }
        if (candidate.isBefore(from) || candidate.isBefore(rule.getStartDate())) {
            throw new IllegalStateException(
                    "recurring rule " + rule.getId() + " produced no occurrence from " + from);
        }
        if (rule.getEndDate() != null && candidate.isAfter(rule.getEndDate())) {
            return null;
        }
        if (rule.isSkipWeekends()) {
            LocalDate shifted = shiftWeekend(candidate);
            boolean outsideWindow = shifted.isBefore(from)
                    || shifted.isBefore(rule.getStartDate())
                    || (rule.getEndDate() != null && shifted.isAfter(rule.getEndDate()));
            if (outsideWindow) {
                return firstOnOrAfter(rule, candidate.plusDays(1));
            }
            return shifted;
        }
        return candidate;
    }

    /** First occurrence strictly after {@code from} - the advancement primitive. */
    public LocalDate next(RecurringTransaction rule, LocalDate from) {
        return firstOnOrAfter(rule, from.plusDays(1));
    }

    /** The next {@code count} occurrences on/after {@code from}, bounded by {@code end_date}. */
    public List<LocalDate> preview(RecurringTransaction rule, LocalDate from, int count) {
        List<LocalDate> dates = new ArrayList<>(count);
        LocalDate cursor = from;
        while (dates.size() < count) {
            LocalDate candidate = firstOnOrAfter(rule, cursor);
            if (candidate == null) {
                break;
            }
            dates.add(candidate);
            cursor = candidate.plusDays(1);
        }
        return dates;
    }

    /** Index estimate at or below the true minimal {@code k}, per frequency. */
    private long lowerBound(RecurringTransaction rule, LocalDate from) {
        long interval = rule.getIntervalCount();
        return switch (rule.getFrequency()) {
            case DAILY -> Math.max(0,
                    Math.floorDiv(ChronoUnit.DAYS.between(rule.getStartDate(), from), interval));
            case WEEKLY -> Math.max(0,
                    Math.floorDiv(ChronoUnit.DAYS.between(weeklyAnchor(rule), from), 7L * interval));
            case CUSTOM -> Math.max(0,
                    Math.floorDiv(ChronoUnit.DAYS.between(rule.getTargetOccurrenceDate(), from), interval));
            case MONTHLY -> {
                long months = 12L * (from.getYear() - rule.getStartDate().getYear())
                        + (from.getMonthValue() - rule.getStartDate().getMonthValue());
                yield Math.max(0, Math.floorDiv(months, interval) - 1);
            }
            case YEARLY -> Math.max(0,
                    Math.floorDiv(from.getYear() - rule.getStartDate().getYear(), interval) - 1);
        };
    }

    /** The raw (pre-weekend-shift) date of occurrence {@code k}. */
    private LocalDate occurrence(RecurringTransaction rule, long k) {
        long interval = rule.getIntervalCount();
        return switch (rule.getFrequency()) {
            case DAILY -> rule.getStartDate().plusDays(k * interval);
            case WEEKLY -> weeklyAnchor(rule).plusDays(k * 7L * interval);
            case MONTHLY -> monthlyOccurrence(rule, k);
            case YEARLY -> PeriodCalculator.addYearsClamped(rule.getStartDate(), k * interval);
            case CUSTOM -> rule.getTargetOccurrenceDate().plusDays(k * interval);
        };
    }

    private LocalDate monthlyOccurrence(RecurringTransaction rule, long k) {
        int desiredDay = rule.getDayOfMonth() != null
                ? rule.getDayOfMonth()
                : rule.getStartDate().getDayOfMonth();
        LocalDate firstOfTarget = rule.getStartDate().withDayOfMonth(1)
                .plusMonths(k * rule.getIntervalCount());
        return firstOfTarget.withDayOfMonth(Math.min(desiredDay, firstOfTarget.lengthOfMonth()));
    }

    private LocalDate weeklyAnchor(RecurringTransaction rule) {
        int desiredDay = rule.getDayOfWeek() != null
                ? rule.getDayOfWeek()
                : rule.getStartDate().getDayOfWeek().getValue();
        return rule.getStartDate().with(ChronoField.DAY_OF_WEEK, desiredDay);
    }

    private static LocalDate shiftWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }
        if (day == DayOfWeek.SUNDAY) {
            return date.plusDays(1);
        }
        return date;
    }
}
