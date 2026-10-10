package com.spendio.analytics.support;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Range and limit rules for the analytics endpoints (ARCHITECTURE.md D.14): a
 * granularity caps how far back a query may reach, and breakdown {@code limit} is
 * clamped so an endpoint can never be asked for an unbounded result set.
 */
public final class AnalyticsRangeValidator {

    /** Max span per granularity, in days: a year of days, three of weeks, five of months. */
    private static final long MAX_DAYS_DAILY = 366;
    private static final long MAX_DAYS_WEEKLY = 1099;
    private static final long MAX_DAYS_MONTHLY = 1860;

    /** Breakdown/top-N cap (D.14: "limit capped at 50"). */
    public static final int MAX_LIMIT = 50;

    private AnalyticsRangeValidator() {
    }

    public static void validate(LocalDate from, LocalDate to, PeriodBucket bucket) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from and to are required")
                    .withProperty("from", from).withProperty("to", to);
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from must not be after to")
                    .withProperty("from", from).withProperty("to", to);
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        long limit = switch (bucket) {
            case DAILY -> MAX_DAYS_DAILY;
            case WEEKLY -> MAX_DAYS_WEEKLY;
            case MONTHLY -> MAX_DAYS_MONTHLY;
        };
        if (days > limit) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Date range is too large for granularity " + bucket.name().toLowerCase())
                    .withProperty("granularity", bucket.name())
                    .withProperty("maxDays", limit)
                    .withProperty("days", days);
        }
    }

    /** Clamps {@code limit} into {@code [1, MAX_LIMIT]}; null/negative -> {@code fallback}. */
    public static int limit(Integer limit, int fallback) {
        if (limit == null || limit <= 0) {
            return fallback;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
