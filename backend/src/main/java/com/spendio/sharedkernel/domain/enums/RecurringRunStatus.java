package com.spendio.sharedkernel.domain.enums;

/**
 * Outcome of one scheduled occurrence of a recurring rule (ARCHITECTURE.md C.3 #13).
 *
 * <p>{@code SKIPPED_DUPLICATE} can never exist as a row - the {@code uk_run} unique key
 * rejects it first - so duplicates are only ever logged. {@code SKIPPED_PAUSED} rows are
 * written by resume catch-up for occurrences dropped beyond the catch-up limit.
 */
public enum RecurringRunStatus {
    SUCCESS,
    SKIPPED,
    FAILED,
    SKIPPED_DUPLICATE,
    SKIPPED_PAUSED
}
