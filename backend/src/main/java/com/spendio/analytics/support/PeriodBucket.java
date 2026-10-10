package com.spendio.analytics.support;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * Bucket granularity for the time-series analytics (ARCHITECTURE.md D.14, E.14
 * {@code granularity}). Labels are stable strings the frontend charts group by:
 * {@code yyyy-MM-dd} (day, week anchored to Monday), {@code yyyy-MM} (month).
 */
public enum PeriodBucket {

    DAILY,
    WEEKLY,
    MONTHLY;

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Accepts {@code day|daily}, {@code week|weekly}, {@code month|monthly}; null = DAILY. */
    public static PeriodBucket parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAILY;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "day", "daily" -> DAILY;
            case "week", "weekly" -> WEEKLY;
            case "month", "monthly" -> MONTHLY;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "granularity must be daily, weekly or monthly")
                    .withProperty("granularity", raw);
        };
    }

    /** The bucket label a day falls into - equal labels are the same bucket. */
    public String label(LocalDate day) {
        return switch (this) {
            case DAILY -> DAY_LABEL.format(day);
            case WEEKLY -> DAY_LABEL.format(mondayOf(day));
            case MONTHLY -> MONTH_LABEL.format(YearMonth.from(day));
        };
    }

    /** First day of the bucket (Monday for weekly). */
    public LocalDate startOf(LocalDate day) {
        return switch (this) {
            case DAILY -> day;
            case WEEKLY -> mondayOf(day);
            case MONTHLY -> day.withDayOfMonth(1);
        };
    }

    private static LocalDate mondayOf(LocalDate day) {
        return day.with(WeekFields.ISO.dayOfWeek(), 1);
    }
}
