package com.spendio.analytics.dto.response;

/**
 * One goal's progress for the dashboard snapshot (ARCHITECTURE.md D.14
 * {@code GET /analytics/goal-progress}); amounts are in the goal's own currency.
 */
public record GoalProgressSummaryResponse(
        Long goalId,
        String name,
        long targetMinor,
        long currentMinor,
        int progressPct,
        boolean onTrack,
        String currencyCode) {
}
