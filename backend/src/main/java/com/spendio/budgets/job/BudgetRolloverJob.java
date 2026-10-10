package com.spendio.budgets.job;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.dto.response.BudgetProgressResponse;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.budgets.service.BudgetAlertService;
import com.spendio.budgets.service.BudgetProgressService;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly rollover (ARCHITECTURE.md D.10, K.5): every active budget with
 * {@code rollover_enabled} whose window has closed carries its unspent allowance into
 * the next canonical window and is then re-evaluated for alerts.
 *
 * <p>Idempotent by construction: the loop only advances while {@code period_end} is
 * strictly before today, so a second run on the same day finds nothing to do, and a
 * job that missed several months catches up window by window (bounded at 240 steps,
 * ~20 years). The carried amount is clamped at zero - overspending never creates
 * budget for the next period (the schema CHECK forbids it too).
 */
@Component
public class BudgetRolloverJob {

    public static final String JOB_NAME = "budget-rollover";

    private static final int MAX_CATCH_UP_WINDOWS = 240;

    private static final Logger log = LoggerFactory.getLogger(BudgetRolloverJob.class);

    private final BudgetRepository budgets;
    private final BudgetProgressService progress;
    private final BudgetAlertService alerts;
    private final JobExecutionLogRepository jobLog;

    public BudgetRolloverJob(BudgetRepository budgets, BudgetProgressService progress,
            BudgetAlertService alerts, JobExecutionLogRepository jobLog) {
        this.budgets = budgets;
        this.progress = progress;
        this.alerts = alerts;
        this.jobLog = jobLog;
    }

    @Scheduled(cron = "${spendio.budgets.rollover-cron:0 30 0 1 * *}")
    @Transactional
    public void scheduledRun() {
        run();
    }

    /** Manual/test entry point: advance every closed rollover window, then re-alert. */
    @Transactional
    public int run() {
        LocalDate today = LocalDate.now();
        JobExecutionLog entry = JobExecutionLog.start(JOB_NAME, Instant.now());
        int advanced = 0;
        try {
            List<Budget> due = budgets.findByDeletedAtIsNullAndStatusAndRolloverEnabledTrueAndPeriodEndLessThan(
                    Budget.Status.ACTIVE, today);
            for (Budget budget : due) {
                if (advance(budget, today)) {
                    advanced++;
                    alerts.evaluate(budget);
                }
            }
            entry.succeed(advanced, "advanced=" + advanced, Instant.now());
        } catch (RuntimeException failure) {
            entry.fail(failure.getMessage(), Instant.now());
            log.warn("budget rollover failed: {}", failure.getMessage());
        } finally {
            jobLog.save(entry);
        }
        return advanced;
    }

    /** Advances one budget across every closed window; false when there was nothing to do. */
    boolean advance(Budget budget, LocalDate today) {
        LocalDate start = budget.getPeriodStart();
        LocalDate end = budget.getPeriodEnd();
        int steps = 0;
        while (end.isBefore(today) && steps < MAX_CATCH_UP_WINDOWS) {
            BudgetProgressResponse closed = progress.compute(budget.getUserId(), budget, start, end);
            long unspent = budget.getAmountMinor() + budget.getRolloverAmountMinor() - closed.spentMinor();
            long carry = Math.max(0, unspent);

            LocalDate nextStart = end.plusDays(1);
            LocalDate nextEnd = nextEnd(budget.getPeriodType(), nextStart,
                    java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
            budget.advancePeriod(nextStart, nextEnd, carry);

            start = nextStart;
            end = nextEnd;
            steps++;
        }
        return steps > 0;
    }

    private static LocalDate nextEnd(Budget.PeriodType type, LocalDate start, long windowDays) {
        return switch (type) {
            case WEEKLY -> start.plusDays(6);
            case MONTHLY -> start.withDayOfMonth(start.lengthOfMonth());
            case QUARTERLY -> start.plusMonths(3).withDayOfMonth(1).minusDays(1);
            case YEARLY -> start.withMonth(12).withDayOfMonth(31);
            case CUSTOM -> start.plusDays(windowDays - 1);
        };
    }
}
