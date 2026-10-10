package com.spendio.analytics.service;

import com.spendio.analytics.dto.response.BudgetUtilizationResponse;
import com.spendio.analytics.dto.response.BudgetUtilizationResponse.Status;
import com.spendio.budgets.domain.PaceStatus;
import com.spendio.budgets.dto.response.BudgetProgressResponse;
import com.spendio.budgets.service.BudgetProgressService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Utilisation for every budget covering today (ARCHITECTURE.md D.14
 * {@code GET /analytics/budget-utilization}). Figures stay in the budget's own
 * currency (D.10) and the pace verdict is folded into a three-state status the
 * charts can colour.
 */
@Service
public class BudgetAnalyticsService {

    private final BudgetProgressService progress;
    private final AnalyticsCacheManager cache;

    public BudgetAnalyticsService(BudgetProgressService progress, AnalyticsCacheManager cache) {
        this.progress = progress;
        this.cache = cache;
    }

    public List<BudgetUtilizationResponse> utilization(Long userId) {
        return cache.get(userId, "budget-utilization", List.class, () -> {
            List<BudgetUtilizationResponse> result = new ArrayList<>();
            for (BudgetProgressResponse p : progress.current(userId)) {
                result.add(new BudgetUtilizationResponse(
                        p.budget().id(),
                        p.budget().name(),
                        p.spentMinor(),
                        p.allowanceMinor(),
                        p.utilisationPct(),
                        p.remainingMinor(),
                        p.projectedSpendMinor(),
                        statusOf(p),
                        p.budget().currencyCode()));
            }
            return result;
        });
    }

    private static Status statusOf(BudgetProgressResponse progress) {
        if (progress.overBudget() || progress.paceStatus() == PaceStatus.EXCEEDED) {
            return Status.OVER_BUDGET;
        }
        if (progress.paceStatus() == PaceStatus.OVER_PACE) {
            return Status.AT_RISK;
        }
        return Status.ON_TRACK;
    }
}
