package com.spendio.budgets.mapper;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.domain.BudgetAlert;
import com.spendio.budgets.dto.response.BudgetAlertResponse;
import com.spendio.budgets.dto.response.BudgetResponse;
import com.spendio.categories.domain.Category;
import org.springframework.stereotype.Component;

/**
 * Budget and alert to their read DTOs. The category is a lazy association - call
 * inside an open transaction (list reads fetch-join it via
 * {@link com.spendio.budgets.spec.BudgetSpecifications#withCategory()}).
 */
@Component
public class BudgetMapper {

    public BudgetResponse toResponse(Budget budget) {
        BudgetResponse.CategoryRef category = budget.getCategory() == null ? null
                : new BudgetResponse.CategoryRef(
                        budget.getCategory().getId(),
                        budget.getCategory().getName(),
                        budget.getCategory().getType(),
                        budget.getCategory().getColor(),
                        budget.getCategory().getIcon());
        return new BudgetResponse(
                budget.getId(),
                budget.getName(),
                category,
                budget.getPeriodType(),
                budget.getPeriodStart(),
                budget.getPeriodEnd(),
                budget.getAmountMinor(),
                budget.getCurrencyCode(),
                budget.getBaseAmountMinor(),
                budget.isRolloverEnabled(),
                budget.getRolloverAmountMinor(),
                budget.getAlertThresholdPct(),
                budget.isAlertEnabled(),
                budget.isIncludeSubcategories(),
                budget.getStatus(),
                budget.getVersion(),
                budget.getCreatedAt());
    }

    public BudgetAlertResponse toAlertResponse(BudgetAlert alert, String budgetName, String currencyCode) {
        return new BudgetAlertResponse(
                alert.getId(),
                alert.getBudgetId(),
                budgetName,
                currencyCode,
                alert.getPeriodStart(),
                alert.getPeriodEnd(),
                alert.getThresholdPct(),
                alert.getBudgetAmountMinor(),
                alert.getSpentAmountMinor(),
                alert.getTriggeredAt(),
                alert.getAcknowledgedAt(),
                alert.getAcknowledgedAt() != null);
    }

    public static BudgetResponse.CategoryRef categoryRef(Category category) {
        return new BudgetResponse.CategoryRef(
                category.getId(), category.getName(), category.getType(), category.getColor(), category.getIcon());
    }
}
