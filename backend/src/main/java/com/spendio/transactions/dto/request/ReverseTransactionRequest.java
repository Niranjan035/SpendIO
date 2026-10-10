package com.spendio.transactions.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Reverse a ledger row with a mirrored entry (ARCHITECTURE.md D.8, E.7): {@code reason}
 * is recorded in {@code reversal_reason} on both rows; {@code occurredOn} dates the
 * mirrored entry.
 */
public record ReverseTransactionRequest(
        @NotBlank @Size(max = 255) String reason,
        @NotNull LocalDate occurredOn) {
}
