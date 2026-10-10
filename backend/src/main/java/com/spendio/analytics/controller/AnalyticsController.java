package com.spendio.analytics.controller;

import com.spendio.analytics.dto.response.AccountBreakdownResponse;
import com.spendio.analytics.dto.response.BudgetUtilizationResponse;
import com.spendio.analytics.dto.response.CashFlowPointResponse;
import com.spendio.analytics.dto.response.CategoryBreakdownResponse;
import com.spendio.analytics.dto.response.GoalProgressSummaryResponse;
import com.spendio.analytics.dto.response.MonthlyComparisonResponse;
import com.spendio.analytics.dto.response.NetWorthPointResponse;
import com.spendio.analytics.dto.response.SavingsRatePointResponse;
import com.spendio.analytics.dto.response.SpendingTrendPointResponse;
import com.spendio.analytics.dto.response.TopSpendingResponse;
import com.spendio.analytics.service.AccountAnalyticsService;
import com.spendio.analytics.service.BudgetAnalyticsService;
import com.spendio.analytics.service.CategoryAnalyticsService;
import com.spendio.analytics.service.GoalAnalyticsService;
import com.spendio.analytics.service.SummaryAnalyticsService;
import com.spendio.analytics.service.TrendAnalyticsService;
import com.spendio.sharedkernel.web.CurrentUserId;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics endpoints (ARCHITECTURE.md D.14, E.14). All reads are user-scoped,
 * range-validated per granularity and served from the per-user analytics cache.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final TrendAnalyticsService trend;
    private final CategoryAnalyticsService category;
    private final SummaryAnalyticsService summary;
    private final BudgetAnalyticsService budget;
    private final GoalAnalyticsService goal;
    private final AccountAnalyticsService account;

    public AnalyticsController(TrendAnalyticsService trend, CategoryAnalyticsService category,
            SummaryAnalyticsService summary, BudgetAnalyticsService budget,
            GoalAnalyticsService goal, AccountAnalyticsService account) {
        this.trend = trend;
        this.category = category;
        this.summary = summary;
        this.budget = budget;
        this.goal = goal;
        this.account = account;
    }

    @GetMapping("/cash-flow")
    public List<CashFlowPointResponse> cashFlow(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String granularity) {
        return trend.cashFlow(userId, from, to, granularity);
    }

    @GetMapping("/spending-trend")
    public List<SpendingTrendPointResponse> spendingTrend(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String granularity) {
        return trend.spendingTrend(userId, from, to, granularity);
    }

    @GetMapping("/income-vs-expense")
    public List<SavingsRatePointResponse> incomeVsExpense(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String granularity) {
        return trend.incomeVsExpense(userId, from, to, granularity);
    }

    @GetMapping("/category-breakdown")
    public List<CategoryBreakdownResponse> categoryBreakdown(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean parentOnly) {
        return category.categoryBreakdown(userId, from, to, type, limit, parentOnly);
    }

    @GetMapping("/top-spending")
    public List<TopSpendingResponse> topSpending(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return category.topSpending(userId, from, to, limit);
    }

    @GetMapping("/monthly-comparison")
    public List<MonthlyComparisonResponse> monthlyComparison(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Integer year) {
        return summary.monthlyComparison(userId, year);
    }

    @GetMapping("/budget-utilization")
    public List<BudgetUtilizationResponse> budgetUtilization(@CurrentUserId Long userId) {
        return budget.utilization(userId);
    }

    @GetMapping("/goal-progress")
    public List<GoalProgressSummaryResponse> goalProgress(@CurrentUserId Long userId) {
        return goal.progress(userId);
    }

    @GetMapping("/net-worth")
    public List<NetWorthPointResponse> netWorth(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return account.netWorth(userId, from, to);
    }

    @GetMapping("/accounts")
    public AccountBreakdownResponse accounts(@CurrentUserId Long userId) {
        return account.accounts(userId);
    }
}
