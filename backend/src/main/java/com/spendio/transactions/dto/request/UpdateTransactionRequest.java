package com.spendio.transactions.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial edit of a ledger row (ARCHITECTURE.md E.6 PATCH /transactions/{id}).
 * {@code version} is the optimistic-lock check when supplied; {@code null} skips it.
 * Blank strings clear description/notes; other {@code null} fields are left untouched.
 */
public record UpdateTransactionRequest(
        Long accountId,
        Long categoryId,
        @Positive Long amountMinor,
        @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code")
        String currencyCode,
        LocalDate occurredOn,
        @Size(max = 255) String description,
        @Size(max = 16383) String notes,
        Integer version) {
}
