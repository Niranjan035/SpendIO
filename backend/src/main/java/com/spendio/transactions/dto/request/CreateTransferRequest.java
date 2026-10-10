package com.spendio.transactions.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Create a transfer as a linked pair of ledger rows (ARCHITECTURE.md D.8, E.8).
 * {@code amountMinor}/{@code currencyCode} describe the source leg (they must match the
 * source account currency); {@code toAmountMinor} overrides the converted amount on the
 * destination leg when the two accounts hold different currencies.
 */
public record CreateTransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @Positive Long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code")
        String currencyCode,
        @Positive Long toAmountMinor,
        @NotNull LocalDate occurredOn,
        @Size(max = 255) String description,
        @Size(max = 16383) String notes) {
}
