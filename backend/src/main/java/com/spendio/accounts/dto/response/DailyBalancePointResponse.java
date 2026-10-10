package com.spendio.accounts.dto.response;

import java.time.LocalDate;

/**
 * One day of a per-account balance series (ARCHITECTURE.md D.6).
 */
public record DailyBalancePointResponse(
        LocalDate date,
        long balanceMinor,
        long balanceBaseMinor) {
}