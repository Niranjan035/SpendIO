package com.spendio.sharedkernel.events;

import java.time.Instant;

/**
 * Published when a goal crosses its configured milestone threshold (ARCHITECTURE.md
 * D.11/K.6).
 *
 * <p>Lives in the kernel so Phase 11's notifications module can subscribe without
 * depending on {@code goals} (B.3). The crossing is derived from contribution history,
 * so re-evaluating a goal that already passed the milestone never re-fires, and
 * {@code dedupeKey} gives the notification side a second idempotency gate
 * {@code goal:{goalId}:milestone:{pct}}.
 */
public record GoalMilestoneEvent(
        Long userId,
        Long goalId,
        String goalName,
        short milestonePct,
        long currentAmountMinor,
        long targetAmountMinor,
        String currencyCode,
        String dedupeKey,
        Instant occurredAt) {

    public static String dedupeKey(Long goalId, short milestonePct) {
        return "goal:" + goalId + ":milestone:" + milestonePct;
    }
}
