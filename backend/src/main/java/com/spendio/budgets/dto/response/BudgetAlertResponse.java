package com.spendio.budgets.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/** One threshold crossing of a budget (ARCHITECTURE.md D.10, E.10). */
public record BudgetAlertResponse(
        Long id,
        Long budgetId,
        String budgetName,
        String currencyCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        short thresholdPct,
        long budgetAmountMinor,
        long spentAmountMinor,
        Instant triggeredAt,
        Instant acknowledgedAt,
        boolean acknowledged) {
}
