package com.spendio.accounts.dto.response;

import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.AccountType;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Account representation for list and detail (ARCHITECTURE.md D.6). The spec's fields
 * are kept verbatim; the balance suffix is the \"with current balance\" read-out the
 * list and detail endpoints are required to show. {@code availableCreditMinor} is
 * populated for CREDIT_CARD accounts only.
 */
public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        String currencyCode,
        long openingBalanceMinor,
        LocalDate openingBalanceDate,
        String institution,
        String last4,
        Long creditLimitMinor,
        Integer billingDay,
        Integer dueDay,
        String icon,
        String color,
        String notes,
        boolean primary,
        AccountStatus status,
        long transactionCount,
        Instant createdAt,
        long balanceMinor,
        long balanceBaseMinor,
        String baseCurrencyCode,
        Long availableCreditMinor) {
}