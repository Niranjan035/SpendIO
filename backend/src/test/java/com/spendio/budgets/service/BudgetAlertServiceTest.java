package com.spendio.budgets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.domain.BudgetAlert;
import com.spendio.budgets.dto.response.BudgetProgressResponse;
import com.spendio.budgets.repository.BudgetAlertRepository;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.sharedkernel.events.BudgetAlertTriggeredEvent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Alert thresholds (ARCHITECTURE.md D.10/K.5): exact integer comparison
 * {@code spent * 100 >= threshold * allowance}, both thresholds per period, and the
 * exists-by-probe that keeps repeat evaluations from creating a second alert.
 */
@ExtendWith(MockitoExtension.class)
class BudgetAlertServiceTest {

    private static final long USER = 1L;
    private static final long BUDGET_ID = 7L;
    private static final LocalDate FROM = LocalDate.of(2026, 2, 1);
    private static final LocalDate TO = LocalDate.of(2026, 2, 28);

    @Mock
    private BudgetRepository budgets;
    @Mock
    private BudgetAlertRepository alerts;
    @Mock
    private BudgetProgressService progress;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BudgetAlertService service;

    @BeforeEach
    void setUp() {
        service = new BudgetAlertService(budgets, alerts, progress, events, transactionManager);
    }

    @Test
    void firesBothThresholdsWhenAllowanceIsExhausted() {
        Budget budget = budget(80, 100_000);
        snapshot(120_000, 100_000);
        when(alerts.existsByBudgetIdAndPeriodStartAndThresholdPct(any(), any(), anyShort())).thenReturn(false);

        int created = service.evaluate(budget);

        assertThat(created).isEqualTo(2);
        var captor = ArgumentCaptor.forClass(BudgetAlert.class);
        verify(alerts, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(BudgetAlert::getThresholdPct)
                .containsExactlyInAnyOrder((short) 80, (short) 100);
        verify(events, org.mockito.Mockito.times(2))
                .publishEvent(any(BudgetAlertTriggeredEvent.class));
    }

    @Test
    void staysSilentUntilTheExactThreshold() {
        Budget budget = budget(80, 100_000);
        // 79.999% of allowance - one cent below 80% must not fire.
        snapshot(79_999, 100_000);

        assertThat(service.evaluate(budget)).isZero();
        verify(alerts, never()).saveAndFlush(any());
    }

    @Test
    void reEvaluationDoesNotDuplicateAnExistingAlert() {
        Budget budget = budget(80, 100_000);
        snapshot(100_000, 100_000); // also crosses 100% -> alert
        when(alerts.existsByBudgetIdAndPeriodStartAndThresholdPct(BUDGET_ID, FROM, (short) 80))
                .thenReturn(true);
        when(alerts.existsByBudgetIdAndPeriodStartAndThresholdPct(BUDGET_ID, FROM, (short) 100))
                .thenReturn(false);

        int created = service.evaluate(budget);

        assertThat(created).isEqualTo(1);
        var captor = ArgumentCaptor.forClass(BudgetAlert.class);
        verify(alerts).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getThresholdPct()).isEqualTo((short) 100);
    }

    @Test
    void skipsAThresholdWithNoAllowance() {
        Budget budget = budget(80, 100_000);
        snapshot(0, 0);
        assertThat(service.evaluate(budget)).isZero();
    }

    private void snapshot(long spent, long allowance) {
        when(progress.compute(eq(USER), any(Budget.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BudgetProgressResponse(
                        null, null, allowance, spent, allowance - spent, null,
                        0, 0, spent >= allowance, null, 0, 0, List.of()));
    }

    private static Budget budget(int threshold, long amount) {
        Budget budget = Budget.create(USER, "Groceries", null, Budget.PeriodType.CUSTOM, FROM, TO,
                amount, "USD", amount, false, (short) threshold, true, true);
        org.springframework.test.util.ReflectionTestUtils.setField(budget, "id", BUDGET_ID);
        return budget;
    }
}