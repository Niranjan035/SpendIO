package com.spendio.accounts.spec;

import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.AccountType;

/**
 * List filter for accounts (ARCHITECTURE.md D.6). {@code q} searches name and
 * institution, {@code includeArchived} widens to archived accounts and
 * {@code hasTransactions} restricts to accounts with (or without) ledger rows. The
 * last one cannot be expressed as a JPA specification - transactions have no entity in
 * this module - so list orchestration switches to a native query when it is set (and
 * when sorting by balance).
 */
public record AccountFilter(
        AccountType type,
        AccountStatus status,
        String currencyCode,
        String q,
        Boolean includeArchived,
        Boolean hasTransactions) {

    public static AccountFilter all() {
        return new AccountFilter(null, null, null, null, null, null);
    }
}