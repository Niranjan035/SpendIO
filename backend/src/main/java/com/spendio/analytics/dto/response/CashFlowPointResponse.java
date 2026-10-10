package com.spendio.analytics.dto.response;

/**
 * One bucket of the income/expense/net series (ARCHITECTURE.md D.14
 * {@code GET /analytics/cash-flow}); {@code period} is the bucket label.
 */
public record CashFlowPointResponse(
        String period,
        long incomeMinor,
        long expenseMinor,
        long netMinor) {
}
