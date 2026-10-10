package com.spendio.goals.dto.request;

import com.spendio.sharedkernel.domain.enums.GoalContributionMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * One goal deposit or withdrawal (ARCHITECTURE.md D.11). {@code amountMinor} is
 * signed (negative = withdrawal, needs a {@code notes} reason). {@code method} or
 * {@code transactionId} choose how it lands: {@code TRANSFER} without an explicit
 * {@code transactionId} creates the paired ledger transfer; a supplied
 * {@code transactionId} links an existing ledger row instead (unique - one row can
 * back only one contribution).
 */
public record CreateContributionRequest(
        @NotNull Long amountMinor,
        LocalDate contributedOn,
        GoalContributionMethod method,
        Long transactionId,
        @Size(max = 500) String notes) {
}
