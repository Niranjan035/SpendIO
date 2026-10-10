package com.spendio.sharedkernel.domain;

import java.time.LocalDate;

/**
 * Pure calendar arithmetic shared by every module that reasons about periods
 * (ARCHITECTURE.md roadmap: {@code sharedkernel/domain/PeriodCalculator}).
 *
 * <p>All methods are anchored: the caller passes the original anchor date and an
 * occurrence number, never a previously computed occurrence, so clamping cannot drift
 * (Jan 31 + 1 month = Feb 28/29, but Jan 31 + 2 months from the anchor = Mar 31).
 */
public final class PeriodCalculator {

    private PeriodCalculator() {
    }

    /** {@code anchor} moved {@code months} forward; day-of-month clamped to the target month. */
    public static LocalDate addMonthsClamped(LocalDate anchor, long months) {
        LocalDate firstOfTarget = anchor.withDayOfMonth(1).plusMonths(months);
        return firstOfTarget.withDayOfMonth(Math.min(anchor.getDayOfMonth(), firstOfTarget.lengthOfMonth()));
    }

    /** {@code anchor} moved {@code years} forward; Feb 29 lands on Feb 28 in non-leap years. */
    public static LocalDate addYearsClamped(LocalDate anchor, long years) {
        LocalDate firstOfTarget = anchor.withDayOfMonth(1).plusYears(years);
        return firstOfTarget.withDayOfMonth(Math.min(anchor.getDayOfMonth(), firstOfTarget.lengthOfMonth()));
    }

    /** {@code anchor} moved {@code days} forward (or back for negative values). */
    public static LocalDate addDays(LocalDate anchor, long days) {
        return anchor.plusDays(days);
    }

    /** {@code anchor} moved {@code weeks} forward (or back for negative values). */
    public static LocalDate addWeeks(LocalDate anchor, long weeks) {
        return anchor.plusWeeks(weeks);
    }
}
