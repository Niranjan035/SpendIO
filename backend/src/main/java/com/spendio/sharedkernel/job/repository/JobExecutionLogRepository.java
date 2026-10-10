package com.spendio.sharedkernel.job.repository;

import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, Long> {
    Optional<JobExecutionLog> findFirstByJobNameOrderByStartedAtDesc(String jobName);
}