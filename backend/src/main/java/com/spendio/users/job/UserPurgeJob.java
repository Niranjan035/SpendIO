package com.spendio.users.job;

import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import com.spendio.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard purge after the grace window (ARCHITECTURE.md D.3, C.4, Phase 14): rows
 * soft-deleted before the grace cutoff are removed for good. Child tables cascade on
 * the user FK and {@code audit_log} is detached via {@code ON DELETE SET NULL}, so the
 * trail survives the purge.
 */
@Component
public class UserPurgeJob {

    public static final String JOB_NAME = "user-purge";

    private static final Logger log = LoggerFactory.getLogger(UserPurgeJob.class);

    private final UserRepository users;
    private final JobExecutionLogRepository jobLog;
    private final int graceDays;

    public UserPurgeJob(UserRepository users, JobExecutionLogRepository jobLog,
            @Value("${spendio.users.purge-grace-days:30}") int graceDays) {
        this.users = users;
        this.jobLog = jobLog;
        this.graceDays = graceDays;
    }

    @Scheduled(cron = "${spendio.users.purge-cron:0 30 4 * * *}")
    @Transactional
    public void scheduledRun() {
        run();
    }

    @Transactional
    public int run() {
        JobExecutionLog entry = JobExecutionLog.start(JOB_NAME, Instant.now());
        int purged = 0;
        try {
            for (Long id : users.findIdsByDeletedAtBefore(Instant.now().minus(graceDays, ChronoUnit.DAYS))) {
                users.deleteById(id);
                purged++;
            }
            entry.succeed(purged, "purged=" + purged, Instant.now());
        } catch (RuntimeException failure) {
            entry.fail(failure.getMessage(), Instant.now());
            log.warn("user purge failed: {}", failure.getMessage());
        } finally {
            jobLog.save(entry);
        }
        return purged;
    }
}
