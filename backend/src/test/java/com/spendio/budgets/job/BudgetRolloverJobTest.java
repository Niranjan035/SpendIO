package com.spendio.budgets.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.dto.response.BudgetProgressResponse;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.budgets.service.BudgetAlertService;
import com.spendio.budgets.service.BudgetProgressService;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Rollover (ARCHITECTURE.md D.10/K.5): unspent allowance is carried into exactly the
 * next canonical window, never below zero, a missed run catches up window by window,
 * and a second run over an already-front-dated budget is a no-op.
 */
@ExtendWith(MockitoExtension.class)
class BudgetRolloverJobTest {

    private static final long USER = 1L;
    private static final long BUDGET_ID = 7L;

    @Mock
    private BudgetRepository budgets;
    @Mock
    private BudgetProgressService progress;
    @Mock
    private BudgetAlertService alerts;
    @Mock
    private JobExecutionLogRepository jobLog;

    private BudgetRolloverJob job;

    @BeforeEach
    void setUp() {
        job = new BudgetRolloverJob(budgets, progress, alerts, jobLog);
    }

    @Test
    void advancesAClosedWindowWithTheUnspentAllowance() {
        Budget budget = weeklyBudget(100_000, LocalDate.now().minusDays(7));
        snapshot(60_000, 100_000);
        when(budgets.findByDeletedAtIsNullAndStatusAndRolloverEnabledTrueAndPeriodEndLessThan(
                Budget.Status.ACTIVE, LocalDate.now()))
                .thenReturn(List.of(budget));

        assertThat(job.run()).isEqualTo(1);

        // allowance 100000 - spent 60000 = 40000 carried into the next week.
        assertThat(budget.getRolloverAmountMinor()).isEqualTo(40_000);
        assertThat(budget.getPeriodStart()).isEqualTo(budget.getPeriodEnd().minusDays(6));
        assertThat(budget.getPeriodEnd().isBefore(LocalDate.now())).isFalse();
        verify(alerts).evaluate(budget);
        verify(jobLog).save(any(JobExecutionLog.class));
    }

    @Test
    void idempotentSecondRunDoesNothingWhenAlreadyCurrent() {
        Budget budget = weeklyBudget(100_000, LocalDate.now().minusDays(7));
        snapshot(60_000, 100_000);
        when(budgets.findByDeletedAtIsNullAndStatusAndRolloverEnabledTrueAndPeriodEndLessThan(
                eq(Budget.Status.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(budget))
                .thenReturn(List.of()); // second run: window already in the future

        assertThat(job.run()).isEqualTo(1);
        long carried = budget.getRolloverAmountMinor();
        assertThat(job.run()).isZero();
        assertThat(budget.getRolloverAmountMinor()).isEqualTo(carried);
        verify(alerts, times(1)).evaluate(budget);
    }

    @Test
    void overspendingNeverCreatesReversibleBudget() {
        Budget budget = weeklyBudget(100_000, LocalDate.now().minusDays(7));
        snapshot(150_000, 100_000); // 50k over

        assertThat(job.advance(budget, LocalDate.now())).isTrue();

        assertThat(budget.getRolloverAmountMinor()).isZero();
    }

    @Test
    void catchesUpMultipleClosedWindows() {
        Budget budget = weeklyBudget(100_000, LocalDate.now().minusDays(14)); // two weeks closed
        snapshot(40_000, 100_000);

        assertThat(job.advance(budget, LocalDate.now())).isTrue();

        // First window carries 60000 (100000-40000); the second carries its 60000 on top of that.
        assertThat(budget.getRolloverAmountMinor()).isEqualTo(120_000);
        assertThat(budget.getPeriodStart()).isEqualTo(LocalDate.now());
        verify(progress, times(2)).compute(eq(USER), eq(budget), any(LocalDate.class), any(LocalDate.class));
    }

    private void snapshot(long spent, long allowance) {
        when(progress.compute(eq(USER), any(Budget.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BudgetProgressResponse(
                        null, null, allowance, spent, allowance - spent, null,
                        0, 0, spent >= allowance, null, 0, 0, List.of()));
    }

    private static Budget weeklyBudget(long amount, LocalDate periodStart) {
        Budget budget = Budget.create(USER, "Groceries", null, Budget.PeriodType.WEEKLY,
                periodStart, periodStart.plusDays(6), amount, "USD", amount, true, (short) 80,
                true, true);
        ReflectionTestUtils.setField(budget, "id", BUDGET_ID);
        return budget;
    }
}