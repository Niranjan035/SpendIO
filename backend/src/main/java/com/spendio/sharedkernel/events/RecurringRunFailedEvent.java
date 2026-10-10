package com.spendio.sharedkernel.events;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Published when a recurring occurrence fails to post (ARCHITECTURE.md J.5).
 *
 * <p>The run row and the rule's retry budget are recorded in a fresh REQUIRES_NEW
 * transaction; this event is published inside that same transaction so Phase 11's
 * notification consumer commits with the failure and never misses one. The dedupe
 * key {@code recurring:{ruleId}:{scheduledFor}} makes repeated attempts on one date
 * a single notification.
 */
public record RecurringRunFailedEvent(
        Long userId,
        Long recurringTransactionId,
        String ruleName,
        LocalDate scheduledFor,
        String message,
        Instant failedAt,
        String dedupeKey) {

    public static String dedupeKey(Long recurringTransactionId, LocalDate scheduledFor) {
        return "recurring:" + recurringTransactionId + ":" + scheduledFor;
    }
}