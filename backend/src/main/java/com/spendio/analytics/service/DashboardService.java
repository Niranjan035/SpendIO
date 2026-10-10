package com.spendio.analytics.service;

import com.spendio.accounts.service.AccountBalanceService;
import com.spendio.accounts.service.AccountSnapshotService;
import com.spendio.analytics.dto.response.DashboardSummaryResponse;
import com.spendio.analytics.dto.response.DashboardSummaryResponse.Compare;
import com.spendio.analytics.dto.response.DashboardSummaryResponse.Deltas;
import com.spendio.analytics.mapper.AnalyticsMapper;
import com.spendio.analytics.repository.AnalyticsQueryRepository;
import com.spendio.analytics.repository.AnalyticsQueryRepository.Totals;
import com.spendio.budgets.dto.response.BudgetProgressResponse;
import com.spendio.budgets.service.BudgetProgressService;
import com.spendio.goals.service.GoalQueryService;
import com.spendio.recurring.dto.response.RecurringTransactionResponse;
import com.spendio.recurring.service.RecurringQueryService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.service.TransactionQueryService;
import com.spendio.transactions.spec.TransactionFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Dashboard reads (ARCHITECTURE.md D.14, E.14): the month summary is one cached
 * round trip carrying net worth (live), period aggregates vs the previous period,
 * and the budget/goal snapshot. Recent and upcoming lists reuse the transactions and
 * recurring services (B.3 allows analytics -> those modules, read-only).
 */
@Service
public class DashboardService {

    private static final int DEFAULT_RECENT = 5;
    private static final int MAX_RECENT = 50;
    private static final int DEFAULT_UPCOMING_DAYS = 7;
    private static final int MAX_UPCOMING_DAYS = 90;

    private final AnalyticsQueryRepository query;
    private final AnalyticsMapper mapper;
    private final AnalyticsCacheManager cache;
    private final BudgetProgressService budgetProgress;
    private final GoalQueryService goalQuery;
    private final AccountBalanceService accountBalances;
    private final AccountSnapshotService snapshots;
    private final BaseCurrencyPort baseCurrency;
    private final TransactionQueryService transactions;
    private final RecurringQueryService recurring;

    public DashboardService(AnalyticsQueryRepository query, AnalyticsMapper mapper, AnalyticsCacheManager cache,
            BudgetProgressService budgetProgress, GoalQueryService goalQuery,
            AccountBalanceService accountBalances, AccountSnapshotService snapshots,
            BaseCurrencyPort baseCurrency, TransactionQueryService transactions,
            RecurringQueryService recurring) {
        this.query = query;
        this.mapper = mapper;
        this.cache = cache;
        this.budgetProgress = budgetProgress;
        this.goalQuery = goalQuery;
        this.accountBalances = accountBalances;
        this.snapshots = snapshots;
        this.baseCurrency = baseCurrency;
        this.transactions = transactions;
        this.recurring = recurring;
    }

    /** {@code month} is {@code yyyy-MM}; defaults to the current month. */
    public DashboardSummaryResponse summary(Long userId, String month) {
        YearMonth period = parseMonth(month);
        String key = "dashboard:summary:" + period;
        return cache.get(userId, key, DashboardSummaryResponse.class, () -> buildSummary(userId, period));
    }

    public List<TransactionResponse> recentTransactions(Long userId, Integer limit) {
        int size = Math.min(limit == null || limit <= 0 ? DEFAULT_RECENT : limit, MAX_RECENT);
        return transactions.search(userId, 0, size, null, TransactionFilter.none()).content();
    }

    public List<RecurringTransactionResponse> upcomingRecurring(Long userId, Integer days) {
        int window = Math.min(days == null || days <= 0 ? DEFAULT_UPCOMING_DAYS : days, MAX_UPCOMING_DAYS);
        return recurring.upcoming(userId, window);
    }

    private DashboardSummaryResponse buildSummary(Long userId, YearMonth period) {
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        YearMonth previous = period.minusMonths(1);

        // Read-through: today's snapshot exists for the net-worth point even before
        // the nightly job ran.
        snapshots.ensureToday(userId);

        Totals current = query.totals(userId, from, to);
        Totals previousTotals = query.totals(userId, previous.atDay(1), previous.atEndOfMonth());
        long savings = current.incomeMinor() - current.expenseMinor();
        long previousSavings = previousTotals.incomeMinor() - previousTotals.expenseMinor();

        String base = baseCurrency.baseCurrencyCode(userId);
        long totalBalance = accountBalances.totals(userId).totalBaseMinor();

        return new DashboardSummaryResponse(
                totalBalance,
                base,
                current.incomeMinor(),
                current.expenseMinor(),
                savings,
                mapper.savingsRatePct(current.incomeMinor(), current.expenseMinor()),
                current.transactionCount(),
                budgetUtilisation(userId),
                BigDecimal.valueOf(goalQuery.summary(userId).overallProgressPct()),
                from,
                to,
                new Compare(
                        previousTotals.incomeMinor(),
                        previousTotals.expenseMinor(),
                        previousSavings,
                        new Deltas(
                                mapper.deltaPct(current.incomeMinor(), previousTotals.incomeMinor()),
                                mapper.deltaPct(current.expenseMinor(), previousTotals.expenseMinor()),
                                mapper.deltaPct(savings, previousSavings))));
    }

    /** Spent / allowance across every budget covering today; zero when there are none. */
    private BigDecimal budgetUtilisation(Long userId) {
        List<BudgetProgressResponse> budgets = budgetProgress.current(userId);
        long allowance = 0;
        long spent = 0;
        for (BudgetProgressResponse progress : budgets) {
            allowance += progress.allowanceMinor();
            spent += progress.spentMinor();
        }
        return mapper.sharePct(spent, allowance);
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "month must be yyyy-MM")
                    .withProperty("month", month);
        }
    }
}
