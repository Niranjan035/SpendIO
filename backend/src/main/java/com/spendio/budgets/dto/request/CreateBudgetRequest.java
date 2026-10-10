package com.spendio.budgets.dto.request;

import com.spendio.budgets.domain.Budget;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Create a budget (ARCHITECTURE.md D.10, E.10). {@code categoryId == null} makes an
 * overall budget covering every expense category; the window must match the canonical
 * boundaries of its {@code periodType} (CUSTOM takes any inclusive window) - that is
 * what makes the monthly rollover job able to advance every budget one period.
 */
public record CreateBudgetRequest(
        @NotBlank @Size(max = 80) String name,
        Long categoryId,
        @NotNull Budget.PeriodType periodType,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull @Positive Long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code")
        String currencyCode,
        Boolean rolloverEnabled,
        @Min(1) @Max(100) Integer alertThresholdPct,
        Boolean alertEnabled,
        Boolean includeSubcategories) {
}
