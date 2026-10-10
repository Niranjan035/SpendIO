package com.spendio.analytics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dashboard summary (ARCHITECTURE.md D.14, E.14 {@code GET /dashboard/summary}):
 * one round trip carrying the month's ledger aggregates, the live net worth, the
 * budget/goal snapshot and the comparison against the previous period.
 * All money figures are base-currency minor units.
 */
public record DashboardSummaryResponse(
        long totalBalanceBaseMinor,
        String baseCurrencyCode,
        long incomeBaseMinor,
        long expenseBaseMinor,
        long savingsBaseMinor,
        BigDecimal savingsRatePct,
        long transactionCount,
        BigDecimal budgetUtilisationPct,
        BigDecimal goalProgressPct,
        LocalDate periodStart,
        LocalDate periodEnd,
        Compare compare) {

    /** Previous period's figures plus the percentage deltas vs the current period. */
    public record Compare(
            long previousIncome,
            long previousExpense,
            long previousSavings,
            Deltas deltas) {
    }

    /** Percent change vs the previous period; {@code null} when the previous is zero. */
    public record Deltas(
            BigDecimal incomePct,
            BigDecimal expensePct,
            BigDecimal savingsPct) {
    }
}
