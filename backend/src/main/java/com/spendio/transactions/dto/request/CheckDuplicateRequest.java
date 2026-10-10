package com.spendio.transactions.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Duplicate probe (ARCHITECTURE.md D.8): finds rows with the same date, amount and
 * currency across the user's ledger, then compares normalised descriptions.
 */
public record CheckDuplicateRequest(
        @NotNull Long accountId,
        @NotNull LocalDate occurredOn,
        @NotNull @Positive Long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code")
        String currencyCode,
        @Size(max = 255) String description) {
}
