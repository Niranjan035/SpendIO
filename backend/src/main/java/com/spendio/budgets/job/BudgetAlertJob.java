package com.spendio.budgets.job;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.budgets.service.BudgetAlertService;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly alert sweep (ARCHITECTURE.md D.10): every budget containing today is
 * re-evaluated, so a threshold crossed by a late-arriving transaction still fires.
 * Each user is evaluated in its own transaction so one bad budget cannot poison the
 * whole sweep.
 */
@Component
public class BudgetAlertJob {

    public static final String JOB_NAME = "budget-alerts";

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertJob.class);

    private final BudgetRepository budgets;
    private final BudgetAlertService alerts;
    private final JobExecutionLogRepository jobLog;

    public BudgetAlertJob(BudgetRepository budgets, BudgetAlertService alerts, JobExecutionLogRepository jobLog) {
        this.budgets = budgets;
        this.alerts = alerts;
        this.jobLog = jobLog;
    }

    @Scheduled(cron = "${spendio.budgets.alert-cron:0 15 0 * * *}")
    public void scheduledRun() {
        run();
    }

    /** Manual/test entry point: one evaluation pass for every user with a live budget. */
    public int run() {
        LocalDate today = LocalDate.now();
        JobExecutionLog entry = JobExecutionLog.start(JOB_NAME, Instant.now());
        int created = 0;
        try {
            Set<Long> users = budgets.findAllAlertCandidates(Budget.Status.ACTIVE, today, today).stream()
                    .map(Budget::getUserId)
                    .collect(Collectors.toSet());
            for (Long userId : users) {
                created += alerts.evaluate(userId, today, today);
            }
            entry.succeed(created, "users=" + users.size() + " alerts=" + created, Instant.now());
        } catch (RuntimeException failure) {
            entry.fail(failure.getMessage(), Instant.now());
            log.warn("budget alert sweep failed: {}", failure.getMessage());
        } finally {
            jobLog.save(entry);
        }
        return created;
    }
}
