package com.spendio.analytics.controller;

import com.spendio.analytics.dto.response.DashboardSummaryResponse;
import com.spendio.analytics.service.DashboardService;
import com.spendio.recurring.dto.response.RecurringTransactionResponse;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.transactions.dto.response.TransactionResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard endpoints (ARCHITECTURE.md E.14): the month summary, the latest ledger
 * rows and the recurring rules coming due - the three reads the dashboard needs to
 * render without N+1 calls.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String month) {
        return dashboard.summary(userId, month);
    }

    @GetMapping("/recent-transactions")
    public List<TransactionResponse> recentTransactions(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Integer limit) {
        return dashboard.recentTransactions(userId, limit);
    }

    @GetMapping("/upcoming-recurring")
    public List<RecurringTransactionResponse> upcomingRecurring(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Integer days) {
        return dashboard.upcomingRecurring(userId, days);
    }
}
