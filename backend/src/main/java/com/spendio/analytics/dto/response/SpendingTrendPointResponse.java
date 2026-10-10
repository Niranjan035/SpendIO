package com.spendio.analytics.dto.response;

/**
 * Expense trend with the prorated budget overlay and running total
 * (ARCHITECTURE.md D.14 {@code GET /analytics/spending-trend}).
 */
public record SpendingTrendPointResponse(
        String period,
        long expenseMinor,
        long budgetMinor,
        long cumulativeMinor) {
}
