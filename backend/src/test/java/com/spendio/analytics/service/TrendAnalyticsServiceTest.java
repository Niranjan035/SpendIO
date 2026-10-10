package com.spendio.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.spendio.analytics.dto.response.CashFlowPointResponse;
import com.spendio.analytics.dto.response.SavingsRatePointResponse;
import com.spendio.analytics.dto.response.SpendingTrendPointResponse;
import com.spendio.analytics.mapper.AnalyticsMapper;
import com.spendio.analytics.repository.AnalyticsQueryRepository;
import com.spendio.analytics.repository.AnalyticsQueryRepository.DailyFlow;
import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.dto.response.BudgetResponse;
import com.spendio.budgets.service.BudgetQueryService;
import com.spendio.budgets.spec.BudgetFilter;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.web.PageResponse;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

/**
 * Time-series bucketing and range rules (ARCHITECTURE.md D.14, "bucket-boundary
 * tests"): weekly buckets anchor on Monday, monthly buckets carry empty months,
 * defaults resolve against today, and the granularity cap rejects over-long ranges.
 * The repository is mocked - the real SQL is covered by
 * {@code AnalyticsQueryRepositoryTest} against MySQL.
 */
class TrendAnalyticsServiceTest {

    private static final long USER = 7;

    private AnalyticsQueryRepository query;
    private BudgetQueryService budgets;
    private TrendAnalyticsService service;

    @BeforeEach
    void setUp() {
        query = mock(AnalyticsQueryRepository.class);
        budgets = mock(BudgetQueryService.class);
        CacheManager cacheManager = mock(CacheManager.class);
        org.springframework.cache.Cache cache = mock(org.springframework.cache.Cache.class);
        org.springframework.cache.Cache.ValueWrapper wrapper = mock(org.springframework.cache.Cache.ValueWrapper.class);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        when(cache.get(any())).thenReturn(wrapper);
        when(wrapper.get()).thenReturn(null);
        service = new TrendAnalyticsService(query, new AnalyticsMapper(),
                new AnalyticsCacheManager(cacheManager), budgets);
    }

    private void stubDailyFlow(List<DailyFlow> rows) {
        when(query.dailyFlow(eq(USER), any(LocalDate.class), any(LocalDate.class))).thenReturn(rows);
    }

    private void stubBudgets(List<BudgetResponse> rows) {
        when(budgets.list(eq(USER), any(BudgetFilter.class), any(), any())).thenReturn(
                PageResponse.of(rows, 0, 100, rows.size()));
    }

    private static BudgetResponse budget(long id, LocalDate from, LocalDate to, long amount) {
        return new BudgetResponse(
                id, "Budget " + id, null, Budget.PeriodType.MONTHLY, from, to, amount, "USD",
                amount, false, 0, (short) 80, true, true, Budget.Status.ACTIVE, 1, Instant.EPOCH);
    }

    @Test
    void weeklyBucketsAnchorOnMonday() {
        stubDailyFlow(List.of(
                new DailyFlow(LocalDate.of(2026, 9, 28), 1_000, 0),
                new DailyFlow(LocalDate.of(2026, 10, 1), 0, 400),
                new DailyFlow(LocalDate.of(2026, 10, 5), 200, 0),
                new DailyFlow(LocalDate.of(2026, 10, 11), 0, 100)));

        List<CashFlowPointResponse> points = service.cashFlow(
                USER, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 11), "weekly");

        assertThat(points).hasSize(2);
        assertThat(points.get(0).period()).isEqualTo("2026-09-28");
        assertThat(points.get(0).incomeMinor()).isEqualTo(1_000);
        assertThat(points.get(0).expenseMinor()).isEqualTo(400);
        assertThat(points.get(0).netMinor()).isEqualTo(600);
        assertThat(points.get(1).period()).isEqualTo("2026-10-05");
        assertThat(points.get(1).incomeMinor()).isEqualTo(200);
        assertThat(points.get(1).expenseMinor()).isEqualTo(100);
    }

    @Test
    void monthlyBucketsCarryEmptyMonths() {
        stubDailyFlow(List.of(
                new DailyFlow(LocalDate.of(2026, 1, 15), 10_000, 0),
                new DailyFlow(LocalDate.of(2026, 2, 10), 0, 3_000),
                new DailyFlow(LocalDate.of(2026, 3, 25), 0, 2_000)));

        List<CashFlowPointResponse> points = service.cashFlow(
                USER, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "monthly");

        assertThat(points).hasSize(3)
                .extracting(CashFlowPointResponse::period, CashFlowPointResponse::incomeMinor,
                        CashFlowPointResponse::expenseMinor, CashFlowPointResponse::netMinor)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2026-01", 10_000L, 0L, 10_000L),
                        org.assertj.core.groups.Tuple.tuple("2026-02", 0L, 3_000L, -3_000L),
                        org.assertj.core.groups.Tuple.tuple("2026-03", 0L, 2_000L, -2_000L));
    }

    @Test
    void dailyDefaultsResolveAgainstToday() {
        stubDailyFlow(List.of());
        List<CashFlowPointResponse> points = service.cashFlow(USER, null, null, "daily");
        assertThat(points).hasSize(30);
    }

    @Test
    void rangeLargerThanTheGranularityCapIsRejected() {
        stubDailyFlow(List.of());
        LocalDate far = LocalDate.now().minusDays(400);
        assertThatThrownBy(() -> service.cashFlow(USER, far, LocalDate.now(), "daily"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void budgetOverlayProratesOverTheBucketsItCovers() {
        stubDailyFlow(List.of());
        stubBudgets(List.of(budget(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 7_000)));

        List<SpendingTrendPointResponse> points = service.spendingTrend(
                USER, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), "daily");

        assertThat(points).hasSize(7);
        assertThat(points).allMatch(point -> point.budgetMinor() == 1_000);
        assertThat(points).allMatch(point -> point.cumulativeMinor() == 0);
    }

    @Test
    void incomeVsExpenseReportsSavingsRate() {
        stubDailyFlow(List.of(
                new DailyFlow(LocalDate.of(2026, 6, 1), 10_000, 0),
                new DailyFlow(LocalDate.of(2026, 6, 5), 0, 2_500)));

        List<SavingsRatePointResponse> points = service.incomeVsExpense(
                USER, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "monthly");

        assertThat(points.get(0).savingsRatePct()).isEqualByComparingTo("75.0");
        assertThat(points.get(0).savingsMinor()).isEqualTo(7_500);
    }
}