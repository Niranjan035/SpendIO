package com.spendio.recurring.job;

import com.spendio.recurring.service.RecurringExecutionService;
import com.spendio.recurring.service.RecurringExecutionService.ExecutionSummary;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recurring executor (ARCHITECTURE.md J.5): fires every 15 minutes, claims due
 * rules and posts their occurrences. Each tick lands in {@code job_execution_log} with
 * its tallies; per-occurrence failures are already recorded by
 * {@link RecurringExecutionService} and never abort the batch.
 */
@Component
public class RecurringTransactionExecutor {

    public static final String JOB_NAME = "recurring-execution";

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionExecutor.class);

    private final RecurringExecutionService execution;
    private final JobExecutionLogRepository jobLog;

    public RecurringTransactionExecutor(RecurringExecutionService execution, JobExecutionLogRepository jobLog) {
        this.execution = execution;
        this.jobLog = jobLog;
    }

    @Scheduled(cron = "${spendio.recurring.cron:0 */15 * * * *}")
    @Transactional
    public void scheduledRun() {
        run();
    }

    /** Manual/test trigger: one claim-and-post batch. */
    @Transactional
    public ExecutionSummary run() {
        JobExecutionLog entry = JobExecutionLog.start(JOB_NAME, Instant.now());
        ExecutionSummary summary = new ExecutionSummary(0, 0, 0, 0);
        try {
            summary = execution.executeDue();
            entry.succeed(summary.claimed(),
                    "claimed=" + summary.claimed() + " posted=" + summary.posted()
                            + " skipped=" + summary.skipped() + " failed=" + summary.failed(),
                    Instant.now());
        } catch (RuntimeException failure) {
            entry.fail(failure.getMessage(), Instant.now());
            log.warn("recurring execution failed: {}", failure.getMessage());
        } finally {
            jobLog.save(entry);
        }
        return summary;
    }
}
