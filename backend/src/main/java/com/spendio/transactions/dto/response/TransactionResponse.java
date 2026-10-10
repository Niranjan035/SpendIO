package com.spendio.transactions.dto.response;

import com.spendio.categories.domain.Category;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Ledger row read (ARCHITECTURE.md D.8). {@code exchangeRate} is frozen in the
 * base->transaction-currency direction; {@code version} is echoed for optimistic
 * locking on PATCH.
 */
public record TransactionResponse(
        Long id,
        AccountRef account,
        CategoryRef category,
        TransactionType type,
        TransactionDirection direction,
        long amountMinor,
        String currencyCode,
        BigDecimal exchangeRate,
        long baseAmountMinor,
        String baseCurrencyCode,
        LocalDate occurredOn,
        String description,
        String merchant,
        String payee,
        String notes,
        TransactionStatus status,
        String transferGroupId,
        Long reversalOfTransactionId,
        Instant reversedAt,
        Long recurringTransactionId,
        Long csvImportId,
        Instant createdAt,
        Instant updatedAt,
        Integer version) {

    public record AccountRef(Long id, String name, String currencyCode) {
    }

    public record CategoryRef(Long id, String name, Category.Type type, String color, String icon) {
    }
}
