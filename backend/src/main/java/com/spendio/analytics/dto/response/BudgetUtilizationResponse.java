package com.spendio.analytics.dto.response;

import java.math.BigDecimal;

/**
 * Utilisation of one active budget (ARCHITECTURE.md D.14
 * {@code GET /analytics/budget-utilization}); money figures are in the budget's own
 * currency so they match the allowance the user set.
 */
public record BudgetUtilizationResponse(
        Long budgetId,
        String name,
        long spentMinor,
        long amountMinor,
        BigDecimal utilisationPct,
        long remainingMinor,
        long projectedMinor,
        Status status,
        String currencyCode) {

    public enum Status {
        ON_TRACK,
        AT_RISK,
        OVER_BUDGET
    }
}
