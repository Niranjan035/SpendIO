package com.spendio.recurring.domain;

import com.spendio.sharedkernel.domain.enums.RecurringRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One attempted occurrence of a recurring rule (ARCHITECTURE.md C.3 #13). Append-only,
 * like {@code JobExecutionLog}: {@code uk_run (recurring_transaction_id, scheduled_for)}
 * is the hard guarantee that a schedule can never post twice (J.5/J.8).
 */
@Entity
@Table(name = "recurring_transaction_runs")
public class RecurringRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recurring_transaction_id", nullable = false)
    private Long recurringTransactionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDate scheduledFor;

    @Column(name = "executed_at", columnDefinition = "DATETIME(3)")
    private Instant executedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecurringRunStatus status;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(3)")
    private Instant createdAt = Instant.now();

    protected RecurringRun() {
    }

    public static RecurringRun claim(Long recurringTransactionId, Long userId, LocalDate scheduledFor) {
        RecurringRun run = new RecurringRun();
        run.recurringTransactionId = recurringTransactionId;
        run.userId = userId;
        run.scheduledFor = scheduledFor;
        run.status = RecurringRunStatus.FAILED;
        run.attempts = 1;
        return run;
    }

    /** Pause catch-up marker: the occurrence existed but was dropped past the catch-up limit. */
    public static RecurringRun skippedPaused(Long recurringTransactionId, Long userId, LocalDate scheduledFor) {
        RecurringRun run = new RecurringRun();
        run.recurringTransactionId = recurringTransactionId;
        run.userId = userId;
        run.scheduledFor = scheduledFor;
        run.status = RecurringRunStatus.SKIPPED_PAUSED;
        return run;
    }

    public void markSuccess(Long transactionId, Instant executedAt) {
        this.status = RecurringRunStatus.SUCCESS;
        this.transactionId = transactionId;
        this.executedAt = executedAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage, Instant executedAt) {
        this.status = RecurringRunStatus.FAILED;
        this.executedAt = executedAt;
        this.errorMessage = truncate(errorMessage, 500);
    }

    public void countAttempt() {
        this.attempts++;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Long getRecurringTransactionId() {
        return recurringTransactionId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getScheduledFor() {
        return scheduledFor;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public RecurringRunStatus getStatus() {
        return status;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public short getAttempts() {
        return attempts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
