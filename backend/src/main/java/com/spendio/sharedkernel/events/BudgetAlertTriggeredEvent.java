package com.spendio.sharedkernel.events;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Published when a budget crosses an alert threshold (ARCHITECTURE.md D.10/K.5).
 *
 * <p>Lives in the kernel so Phase 11's notifications module can subscribe without
 * depending on {@code budgets} (B.3); the alert row itself is the durable record and
 * {@code dedupeKey} is the notification-side idempotency key
 * {@code budget:{budgetId}:{periodStart}:{thresholdPct}}.
 */
public record BudgetAlertTriggeredEvent(
        Long userId,
        Long budgetId,
        String budgetName,
        LocalDate periodStart,
        LocalDate periodEnd,
        short thresholdPct,
        long spentMinor,
        long budgetAmountMinor,
        String currencyCode,
        String dedupeKey,
        Instant triggeredAt) {

    public static String dedupeKey(Long budgetId, LocalDate periodStart, short thresholdPct) {
        return "budget:" + budgetId + ":" + periodStart + ":" + thresholdPct;
    }
}
