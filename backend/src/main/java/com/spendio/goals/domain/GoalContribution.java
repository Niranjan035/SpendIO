package com.spendio.goals.domain;

import com.spendio.sharedkernel.domain.BaseEntity;
import com.spendio.sharedkernel.domain.enums.GoalContributionMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One deposit or withdrawal against a goal (ARCHITECTURE.md C.3 #17, D.11).
 * {@code amount_minor} is signed: positive deposits, negative withdrawals; the DB
 * CHECK keeps zero out. {@code transaction_id} optionally points at the ledger row a
 * transfer-linked contribution created (unique index: one ledger row backs at most
 * one contribution).
 *
 * <p>Deliberately not soft-deletable: contributions are financial history, and
 * {@code DELETE /goals/{id}/contributions/{contributionId}} removes the row only
 * after the goal's cached total is adjusted alongside it.
 */
@Entity
@Table(name = "goal_contributions")
public class GoalContribution extends BaseEntity {

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "contributed_on", nullable = false)
    private LocalDate contributedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalContributionMethod method;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(length = 500)
    private String notes;

    protected GoalContribution() {
    }

    public static GoalContribution create(Long userId, Long goalId, long amountMinor, String currencyCode,
            LocalDate contributedOn, GoalContributionMethod method, Long transactionId, String notes) {
        GoalContribution contribution = new GoalContribution();
        contribution.userId = userId;
        contribution.goalId = goalId;
        contribution.amountMinor = amountMinor;
        contribution.currencyCode = currencyCode;
        contribution.contributedOn = contributedOn;
        contribution.method = method;
        contribution.transactionId = transactionId;
        contribution.notes = notes;
        return contribution;
    }

    public Long getGoalId() {
        return goalId;
    }

    public Long getUserId() {
        return userId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getContributedOn() {
        return contributedOn;
    }

    public GoalContributionMethod getMethod() {
        return method;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getNotes() {
        return notes;
    }
}
