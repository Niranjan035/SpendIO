package com.spendio.analytics.dto.response;

import com.spendio.sharedkernel.domain.enums.AccountType;
import java.time.LocalDate;
import java.util.List;

/**
 * One day of net-worth history from {@code account_snapshots} (ARCHITECTURE.md D.14
 * {@code GET /analytics/net-worth}); all values in base-currency minor units.
 */
public record NetWorthPointResponse(
        LocalDate date,
        long totalMinor,
        List<ByAccount> byAccount) {

    public record ByAccount(
            Long accountId,
            String name,
            AccountType type,
            long balanceBaseMinor) {
    }
}
