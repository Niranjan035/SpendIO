package com.spendio.recurring.dto.response;

import com.spendio.sharedkernel.domain.enums.RecurringRunStatus;
import java.time.Instant;
import java.time.LocalDate;

/** One execution-history row (ARCHITECTURE.md D.9). */
public record RecurringRunResponse(
        Long id,
        LocalDate scheduledFor,
        Instant executedAt,
        RecurringRunStatus status,
        Long transactionId,
        short attempts,
        String errorMessage) {
}
