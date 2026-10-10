package com.spendio.accounts.job;

import com.spendio.accounts.service.AccountSnapshotService;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly net-worth snapshot (ARCHITECTURE.md Phase 13): captures every active
 * account's balance so net-worth history reads {@code account_snapshots} instead of
 * re-summing the ledger. Runs after the day's local midnight; idempotent (upserts on
 * {@code (account_id, snapshot_date)}), so a missed day is repaired on the next run.
 */
@Component
public class AccountSnapshotJob {

    public static final String JOB_NAME = "account-snapshot";

    private static final Logger log = LoggerFactory.getLogger(AccountSnapshotJob.class);

    private final AccountSnapshotService snapshots;
    private final JobExecutionLogRepository jobLog;

    public AccountSnapshotJob(AccountSnapshotService snapshots, JobExecutionLogRepository jobLog) {
        this.snapshots = snapshots;
        this.jobLog = jobLog;
    }

    @Scheduled(cron = "${spendio.accounts.snapshot-cron:0 10 0 * * *}")
    @Transactional
    public void scheduledRun() {
        run();
    }

    @Transactional
    public int run() {
        JobExecutionLog entry = JobExecutionLog.start(JOB_NAME, Instant.now());
        int written = 0;
        try {
            written = snapshots.captureAll();
            entry.succeed(written, "snapshots_written=" + written, Instant.now());
        } catch (RuntimeException failure) {
            entry.fail(failure.getMessage(), Instant.now());
            log.warn("account snapshot job failed: {}", failure.getMessage());
        } finally {
            jobLog.save(entry);
        }
        return written;
    }
}
