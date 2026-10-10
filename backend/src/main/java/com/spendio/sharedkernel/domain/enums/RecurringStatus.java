package com.spendio.sharedkernel.domain.enums;

/**
 * Lifecycle of a recurring rule (ARCHITECTURE.md J.5/J.8): ACTIVE is picked up by the
 * scheduler; PAUSED skips generation but remembers the schedule; FAILED means the retry
 * budget is exhausted; COMPLETED means the schedule produced its last occurrence;
 * CANCELLED is terminal and never resumes.
 */
public enum RecurringStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
