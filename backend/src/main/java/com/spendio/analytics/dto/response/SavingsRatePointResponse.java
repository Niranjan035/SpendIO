package com.spendio.analytics.dto.response;

import java.math.BigDecimal;

/**
 * Income vs expense with the savings rate for one bucket (ARCHITECTURE.md D.14
 * {@code GET /analytics/income-vs-expense}); {@code savingsRatePct} is null when
 * there was no income in the bucket.
 */
public record SavingsRatePointResponse(
        String period,
        long incomeMinor,
        long expenseMinor,
        long savingsMinor,
        BigDecimal savingsRatePct) {
}
