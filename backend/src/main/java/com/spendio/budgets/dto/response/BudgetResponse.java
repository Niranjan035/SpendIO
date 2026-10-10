package com.spendio.budgets.dto.response;

import com.spendio.budgets.domain.Budget;
import com.spendio.categories.domain.Category;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Budget read (ARCHITECTURE.md D.10). The category is echoed as a ref so the list can
 * render name/colour without a second call; {@code version} lets the edit form hand the
 * optimistic lock back on PATCH.
 */
public record BudgetResponse(
        Long id,
        String name,
        CategoryRef category,
        Budget.PeriodType periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        long amountMinor,
        String currencyCode,
        long baseAmountMinor,
        boolean rolloverEnabled,
        long rolloverAmountMinor,
        short alertThresholdPct,
        boolean alertEnabled,
        boolean includeSubcategories,
        Budget.Status status,
        Integer version,
        Instant createdAt) {

    public record CategoryRef(Long id, String name, Category.Type type, String color, String icon) {
    }
}
