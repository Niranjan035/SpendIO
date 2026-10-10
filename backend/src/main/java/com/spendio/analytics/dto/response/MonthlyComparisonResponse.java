package com.spendio.analytics.dto.response;

import java.math.BigDecimal;

/**
 * Month-by-month income/expense/net for a year with the net change vs the previous
 * month (ARCHITECTURE.md D.14 {@code GET /analytics/monthly-comparison}).
 * {@code month} is {@code yyyy-MM}.
 */
public record MonthlyComparisonResponse(
        String month,
        long incomeMinor,
        long expenseMinor,
        long netMinor,
        BigDecimal vsPreviousMonthPct) {
}
