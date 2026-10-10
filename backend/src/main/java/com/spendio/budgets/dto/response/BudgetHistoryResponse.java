package com.spendio.budgets.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Historical utilisation for one budget (ARCHITECTURE.md K.5): successive windows of
 * the budget's own period type, oldest first, computed live from the ledger - never
 * from a cache, so a corrected transaction retroactively corrects history too.
 */
public record BudgetHistoryResponse(Long budgetId, List<PeriodSlice> periods) {

    public record PeriodSlice(
            LocalDate periodStart,
            LocalDate periodEnd,
            long allowanceMinor,
            long spentMinor,
            BigDecimal utilisationPct,
            boolean overBudget,
            boolean current) {
    }
}
