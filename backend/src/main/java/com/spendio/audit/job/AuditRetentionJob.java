package com.spendio.audit.job;

import com.spendio.audit.service.AuditLogService;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention (ARCHITECTURE.md C.3 #22, D.15): purges audit rows older than the
 * configured window. Observability row via {@code job_execution_log} like every job.
 */
@Component
public class AuditRetentionJob {

    public static final String JOB_NAME = "audit-retention";

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditLogService auditLog;
    private final JobExecutionLogRepository jobLog;
    private final int retentionDays;

    public AuditRetentionJob(AuditLogService auditLog, JobExecutionLogRepository jobLog,
            @Value("${spendio.audit.retention-days:90}") int retentionDays) {
        this.auditLog = auditLog;
        this.jobLog = jobLog;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${spendio.audit.retention-cron:0 10 4 * * *}")
    @Transactional
    public void scheduledRun() {
        run();
    }

    @Transactional
    public int run() {
        JobExecutionLog entry = JobExecutionLog.start(JOB_NAME, Instant.now());
        int purged = 0;
        try {
            purged = auditLog.purgeOlderThan(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
            entry.succeed(purged, "purged=" + purged, Instant.now());
        } catch (RuntimeException failure) {
            entry.fail(failure.getMessage(), Instant.now());
            log.warn("audit retention failed: {}", failure.getMessage());
        } finally {
            jobLog.save(entry);
        }
        return purged;
    }
}
