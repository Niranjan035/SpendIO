package com.spendio.analytics.dto.response;

import com.spendio.sharedkernel.domain.enums.AccountType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Balance breakdown by account and by type (ARCHITECTURE.md D.14
 * {@code GET /analytics/accounts}). {@code balanceMinor} is the account's own
 * currency; {@code balanceBaseMinor} is converted to the reporting currency.
 */
public record AccountBreakdownResponse(
        long totalBaseMinor,
        String baseCurrencyCode,
        List<AccountPoint> accounts,
        List<TypeTotal> byType) {

    public record AccountPoint(
            Long accountId,
            String name,
            AccountType type,
            String currencyCode,
            long balanceMinor,
            long balanceBaseMinor,
            BigDecimal sharePct) {
    }

    public record TypeTotal(
            AccountType type,
            long totalBaseMinor) {
    }
}
