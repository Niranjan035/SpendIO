package com.spendio.goals.dto.response;

import com.spendio.sharedkernel.domain.enums.GoalContributionMethod;
import java.time.LocalDate;

/**
 * One contribution (ARCHITECTURE.md D.11): signed amount in the goal's currency.
 * {@code transactionId} is the ledger row a transfer-linked contribution created or
 * linked.
 */
public record GoalContributionResponse(
        Long id,
        Long goalId,
        long amountMinor,
        LocalDate contributedOn,
        GoalContributionMethod method,
        Long transactionId,
        String notes) {
}
