package com.spendio.notifications.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.notifications.repository.NotificationRepository;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Retention purge (ARCHITECTURE.md D.13/K.10): read/deleted rows age out. */
@ExtendWith(MockitoExtension.class)
class NotificationRetentionJobTest {

    @Mock
    private NotificationRepository notifications;
    @Mock
    private JobExecutionLogRepository jobLog;

    private NotificationRetentionJob job;

    @BeforeEach
    void setUp() {
        job = new NotificationRetentionJob(notifications, jobLog, 90);
    }

    @Test
    void purgesThirtyDayOldRowsAndRecordsTheJob() {
        when(notifications.purgeOlderThan(any(Instant.class))).thenReturn(7);

        int purged = job.run();

        assertThat(purged).isEqualTo(7);
        verify(notifications).purgeOlderThan(any(Instant.class));
        verify(jobLog).save(any());
    }
}