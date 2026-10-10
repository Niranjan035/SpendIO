package com.spendio.budgets.spec;

import com.spendio.budgets.domain.Budget;
import java.time.LocalDate;

/**
 * Search filter for {@code GET /budgets} (ARCHITECTURE.md E.10): every field optional
 * and AND-ed. {@code activeInPeriod} keeps only windows containing that date.
 */
public record BudgetFilter(
        Budget.Status status,
        Budget.PeriodType periodType,
        Long categoryId,
        LocalDate activeInPeriod) {

    public static BudgetFilter none() {
        return new BudgetFilter(null, null, null, null);
    }
}
