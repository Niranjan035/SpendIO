package com.spendio.budgets.dto.response;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.domain.PaceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Budget progress (ARCHITECTURE.md D.10): every money figure is in the budget's own
 * currency (ledger base sums are converted once through {@code LedgerFx}), so the
 * numbers a user sees always match the allowance they set. {@code projectedSpendMinor}
 * is the straight-line extrapolation {@code spent / daysElapsed * daysInPeriod};
 * {@code paceStatus} compares actual spend to that expectation with a +/-10% band.
 */
public record BudgetProgressResponse(
        BudgetResponse budget,
        Period period,
        long allowanceMinor,
        long spentMinor,
        long remainingMinor,
        BigDecimal utilisationPct,
        long projectedSpendMinor,
        long dailyAllowanceMinor,
        boolean overBudget,
        PaceStatus paceStatus,
        int daysElapsed,
        int daysRemaining,
        List<CategorySlice> categoryBreakdown) {

    public record Period(LocalDate start, LocalDate end, Budget.PeriodType type) {
    }

    /** One category's contribution to the spent total, with its share of it. */
    public record CategorySlice(Long categoryId, String name, String color, long spentMinor, BigDecimal sharePct) {
    }
}
