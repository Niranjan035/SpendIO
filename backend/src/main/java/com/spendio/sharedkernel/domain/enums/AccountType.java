package com.spendio.sharedkernel.domain.enums;

/**
 * Account kinds (ARCHITECTURE.md C.3 #8). Stored as a MySQL ENUM.
 */
public enum AccountType {
    CASH,
    BANK,
    CREDIT_CARD,
    WALLET,
    INVESTMENT,
    LOAN
}