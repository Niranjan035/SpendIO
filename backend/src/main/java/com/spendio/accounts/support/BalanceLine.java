package com.spendio.accounts.support;

import com.spendio.sharedkernel.domain.enums.AccountType;

/**
 * Balance read-out for one account (ARCHITECTURE.md D.6): opening balance carried on
 * the account plus the signed sum of live ledger rows. Lives in support (not
 * ..repository..) so the "repositories are interfaces" ArchUnit rule holds.
 */
public record BalanceLine(Long accountId, AccountType type, String currencyCode, long openingBalanceMinor, long netMinor) {

    public long balance() {
        return openingBalanceMinor + netMinor;
    }
}