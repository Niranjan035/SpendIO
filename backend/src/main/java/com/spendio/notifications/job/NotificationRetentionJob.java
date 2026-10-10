package com.spendio.notifications.job;

import com.spendio.notifications.repository.NotificationRepository;
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
 * Retention (ARCHITECTURE.md D.13, K.10): hard-purges notifications older than
 * {@code retention-days} once they are read or deleted, so the inbox never grows
 * without bound. Hard delete is safe because {@code budget_alerts.notification_id}
 * is ON DELETE SET NULL.
 */
@Component
public class NotificationRetentionJob {

    public static final String JOB_NAME = "notification-retention";

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);

    private final NotificationRepository notifications;
    private final JobExecutionLogRepository jobLog;
    private final int retentionDays;

    public NotificationRetentionJob(NotificationRepository notifications,
            JobExecutionLogRepository jobLog,
            @Value("${spendio.notifications.retention-days:90}") int retentionDays) {
        this.notifications = notifications;
        this.jobLog = jobLog;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${spendio.notifications.retention-cron:0 5 3 * * *}")
    @Transactional
    public void scheduledRun() {
        run();
    }

    @Transactional
    public int run() {
        JobExecutionLog entry = JobExecutionLog.start(JOB_NAME, Instant.now());
        int purged = 0;
        try {
            purged = notifications.purgeOlderThan(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
            entry.succeed(purged, "purged=" + purged, Instant.now());
        } catch (RuntimeException failure) {
            entry.fail(failure.getMessage(), Instant.now());
            log.warn("notification retention failed: {}", failure.getMessage());
        } finally {
            jobLog.save(entry);
        }
        return purged;
    }
}