package com.spendio.accounts.dto.response;

import java.time.LocalDate;

/**
 * Current balance for one account (ARCHITECTURE.md D.6).
 */
public record AccountBalanceResponse(
        Long accountId,
        String currencyCode,
        long balanceMinor,
        String baseCurrencyCode,
        long balanceBaseMinor,
        LocalDate asOf,
        Long availableCreditMinor) {
}