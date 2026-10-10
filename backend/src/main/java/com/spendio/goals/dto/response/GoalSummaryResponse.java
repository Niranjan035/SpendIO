package com.spendio.goals.dto.response;

import java.util.List;

/**
 * Aggregate goal progress for the dashboard (ARCHITECTURE.md D.11/E.11
 * {@code GET /goals/summary}). Totals are summed in the user's base currency - goals
 * may be held in different currencies, so each target/current pair is converted at
 * today's rate before adding.
 */
public record GoalSummaryResponse(
        int activeCount,
        int completedCount,
        long totalTargetBaseMinor,
        long totalCurrentBaseMinor,
        long remainingBaseMinor,
        int overallProgressPct,
        String baseCurrencyCode,
        List<GoalResponse> goals) {
}
