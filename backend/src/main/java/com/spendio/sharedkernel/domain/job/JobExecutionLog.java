package com.spendio.sharedkernel.domain.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One run of a {@code @Scheduled} worker (ARCHITECTURE.md A.5). Every job writes a row
 * so an operator can see when it last ran, whether it failed and how much it touched.
 */
@Entity
@Table(name = "job_execution_log")
public class JobExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 80)
    private String jobName;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", nullable = false, updatable = false, columnDefinition = "DATETIME(3)")
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "DATETIME(3)")
    private Instant finishedAt;

    @Column(name = "affected_rows", nullable = false)
    private int affectedRows;

    @Column(columnDefinition = "TEXT")
    private String detail;

    protected JobExecutionLog() {
    }

    public static JobExecutionLog start(String jobName, Instant startedAt) {
        JobExecutionLog log = new JobExecutionLog();
        log.jobName = jobName;
        log.status = "RUNNING";
        log.startedAt = startedAt;
        return log;
    }

    public void succeed(int affectedRows, String detail, Instant finishedAt) {
        this.status = "SUCCESS";
        this.affectedRows = affectedRows;
        this.detail = detail;
        this.finishedAt = finishedAt;
    }

    public void fail(String detail, Instant finishedAt) {
        this.status = "FAILED";
        this.detail = detail;
        this.finishedAt = finishedAt;
    }

    public Long getId() {
        return id;
    }

    public String getJobName() {
        return jobName;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public String getDetail() {
        return detail;
    }
}