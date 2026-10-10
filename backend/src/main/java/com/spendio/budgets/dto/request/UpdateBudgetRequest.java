package com.spendio.budgets.dto.request;

import com.spendio.budgets.domain.Budget;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Partial budget edit (ARCHITECTURE.md D.10). A {@code null} {@code categoryId} on an
 * edit cannot clear the category - clearing would silently turn a category budget into
 * an overall one and step around the overlap rule - so the category is only changeable
 * to another id; use DELETE for removal semantics.
 */
public record UpdateBudgetRequest(
        @Size(max = 80) String name,
        Long categoryId,
        Budget.PeriodType periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        @Positive Long amountMinor,
        @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code")
        String currencyCode,
        Boolean rolloverEnabled,
        @Min(1) @Max(100) Integer alertThresholdPct,
        Boolean alertEnabled,
        Boolean includeSubcategories,
        Budget.Status status,
        Integer version) {
}
