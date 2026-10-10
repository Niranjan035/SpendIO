package com.spendio.budgets.domain;

import com.spendio.sharedkernel.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One threshold crossing of one budget for one period (ARCHITECTURE.md C.3 #15).
 * The unique key {@code (budget_id, period_start, threshold_pct)} is what makes
 * "exactly once per threshold per period" a database guarantee rather than a
 * service-layer hope; the insert is allowed to lose that race and stay silent.
 *
 * <p>{@code notification_id} stays null until Phase 11 adds the notifications table
 * and its FK; the alert itself (and the event published with it) already carries
 * everything a notification needs.
 */
@Entity
@Table(name = "budget_alerts")
public class BudgetAlert extends BaseEntity {

    @Column(name = "budget_id", nullable = false)
    private Long budgetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "threshold_pct", nullable = false)
    private short thresholdPct;

    @Column(name = "budget_amount_minor", nullable = false)
    private long budgetAmountMinor;

    @Column(name = "spent_amount_minor", nullable = false)
    private long spentAmountMinor;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "notification_id")
    private Long notificationId;

    protected BudgetAlert() {
    }

    public static BudgetAlert trigger(Long budgetId, Long userId, LocalDate periodStart, LocalDate periodEnd,
            short thresholdPct, long budgetAmountMinor, long spentAmountMinor, Instant triggeredAt) {
        BudgetAlert alert = new BudgetAlert();
        alert.budgetId = budgetId;
        alert.userId = userId;
        alert.periodStart = periodStart;
        alert.periodEnd = periodEnd;
        alert.thresholdPct = thresholdPct;
        alert.budgetAmountMinor = budgetAmountMinor;
        alert.spentAmountMinor = spentAmountMinor;
        alert.triggeredAt = triggeredAt;
        return alert;
    }

    public void acknowledge(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    /** Phase 11: back-links the notification row that rendered this alert. */
    public void linkNotification(Long notificationId) {
        this.notificationId = notificationId;
    }

    public Long getBudgetId() {
        return budgetId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public short getThresholdPct() {
        return thresholdPct;
    }

    public long getBudgetAmountMinor() {
        return budgetAmountMinor;
    }

    public long getSpentAmountMinor() {
        return spentAmountMinor;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Long getNotificationId() {
        return notificationId;
    }
}
