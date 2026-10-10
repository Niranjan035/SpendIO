package com.spendio.transactions.dto.request;

import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create a ledger row (ARCHITECTURE.md D.8). {@code exchangeRate} is an optional frozen
 * rate override in the column's base->transaction-currency direction; when absent the
 * daily series supplies the rate. {@code direction} is only accepted for ADJUSTMENT
 * (INCOME/EXPENSE signs are derived from {@code type}).
 */
public record CreateTransactionRequest(
        @NotNull Long accountId,
        Long categoryId,
        @NotNull TransactionType type,
        TransactionDirection direction,
        @NotNull @Positive Long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code")
        String currencyCode,
        @Positive BigDecimal exchangeRate,
        @NotNull LocalDate occurredOn,
        @Size(max = 255) String description,
        @Size(max = 160) String merchant,
        @Size(max = 160) String payee,
        @Size(max = 16383) String notes,
        TransactionStatus status,
        @Size(max = 64) String dedupeKey) {
}
