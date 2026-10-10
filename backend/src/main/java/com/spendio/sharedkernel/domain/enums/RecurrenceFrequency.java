package com.spendio.sharedkernel.domain.enums;

/**
 * How often a recurring rule repeats (ARCHITECTURE.md C.3 #12). {@code CUSTOM} uses
 * {@code interval_count} days between occurrences anchored at {@code target_occurrence_date}.
 */
public enum RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM
}
