package com.spendio.accounts.dto.response;

import com.spendio.sharedkernel.domain.enums.AccountType;
import java.util.Map;

/**
 * Roll-up of every non-deleted account for a user (ARCHITECTURE.md D.6). Values are
 * converted to the user's base currency; per-currency raw sums are attached so the UI
 * can render a multi-currency breakdown without another call.
 */
public record AccountTotalsResponse(
        long totalBaseMinor,
        String baseCurrencyCode,
        Map<AccountType, Long> byType,
        Map<String, Long> byCurrency) {
}