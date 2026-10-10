package com.spendio.goals.domain;

import com.spendio.accounts.domain.Account;
import com.spendio.sharedkernel.domain.SoftDeletableEntity;
import com.spendio.sharedkernel.domain.enums.GoalStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A savings goal (ARCHITECTURE.md C.3 #16, D.11): target amount in one currency with
 * a {@code current_amount_minor} cache of {@code SUM(goal_contributions.amount_minor)}.
 *
 * <p>The cache is maintained by {@code GoalContributionService} through atomic
 * guarded UPDATEs (never read-modify-write, so concurrent deposits cannot lose an
 * increment) and verified nightly by {@code GoalReconciliationJob}. Completing a goal
 * is explicit ({@code /complete}) or automatic once the cache reaches the target;
 * both paths go through {@link #complete(Instant)} so {@code completed_at} is never
 * null on a COMPLETED row (DB CHECK).
 */
@Entity
@Table(name = "goals")
public class Goal extends SoftDeletableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "target_amount_minor", nullable = false)
    private long targetAmountMinor;

    @Column(name = "current_amount_minor", nullable = false)
    private long currentAmountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status = GoalStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_account_id")
    private Account linkedAccount;

    /** 0 (lowest) .. 5 (highest). */
    @Column(nullable = false)
    private short priority;

    @Column(length = 40)
    private String icon;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 7)
    private String color;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "milestone_pct", nullable = false)
    private short milestonePct = 90;

    protected Goal() {
    }

    public static Goal create(Long userId, String name, String description, long targetAmountMinor,
            String currencyCode, LocalDate targetDate, Account linkedAccount, short priority,
            String icon, String color, short milestonePct) {
        Goal goal = new Goal();
        goal.userId = userId;
        goal.name = name;
        goal.description = description;
        goal.targetAmountMinor = targetAmountMinor;
        goal.currencyCode = currencyCode;
        goal.targetDate = targetDate;
        goal.linkedAccount = linkedAccount;
        goal.priority = priority;
        goal.icon = icon;
        goal.color = color;
        goal.milestonePct = milestonePct;
        return goal;
    }

    /** Explicit or automatic completion; safe if the two race (idempotent). */
    public void complete(Instant now) {
        if (this.status == GoalStatus.COMPLETED) {
            return;
        }
        this.status = GoalStatus.COMPLETED;
        this.completedAt = now;
    }

    public void reopen() {
        this.status = GoalStatus.ACTIVE;
        this.completedAt = null;
    }

    public void pause() {
        this.status = GoalStatus.PAUSED;
    }

    public void cancel() {
        this.status = GoalStatus.CANCELLED;
    }

    /** Contribution guard: only an ACTIVE goal accepts money (D.11). */
    public void requireAcceptsContributions() {
        if (status == GoalStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.GOAL_ALREADY_COMPLETED, "Goal is already completed")
                    .withProperty("goalId", getId());
        }
        if (status != GoalStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("status", "goal is not active (status=" + status + ")");
        }
    }

    public boolean hasReachedTarget() {
        return currentAmountMinor >= targetAmountMinor;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTargetAmountMinor(long targetAmountMinor) {
        this.targetAmountMinor = targetAmountMinor;
    }

    public void setCurrentAmountMinor(long currentAmountMinor) {
        this.currentAmountMinor = currentAmountMinor;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public void setLinkedAccount(Account linkedAccount) {
        this.linkedAccount = linkedAccount;
    }

    public void setPriority(short priority) {
        this.priority = priority;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setMilestonePct(short milestonePct) {
        this.milestonePct = milestonePct;
    }

    public void setStatus(GoalStatus status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getTargetAmountMinor() {
        return targetAmountMinor;
    }

    public long getCurrentAmountMinor() {
        return currentAmountMinor;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public Account getLinkedAccount() {
        return linkedAccount;
    }

    public Long getLinkedAccountId() {
        return linkedAccount == null ? null : linkedAccount.getId();
    }

    public short getPriority() {
        return priority;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public short getMilestonePct() {
        return milestonePct;
    }
}
