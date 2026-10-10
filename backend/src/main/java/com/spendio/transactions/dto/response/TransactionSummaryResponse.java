package com.spendio.transactions.dto.response;

/**
 * Totals for the current filter (ARCHITECTURE.md D.8): all matching rows in
 * {@code count}; money sums exclude VOID and TRANSFER rows so the figures reconcile
 * with hand-computed income/expense fixtures.
 */
public record TransactionSummaryResponse(
        long count,
        long incomeBaseMinor,
        long expenseBaseMinor,
        long netBaseMinor,
        String baseCurrencyCode) {
}
