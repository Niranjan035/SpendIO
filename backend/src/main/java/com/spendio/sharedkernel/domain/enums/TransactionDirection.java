package com.spendio.sharedkernel.domain.enums;

/**
 * The sign of a ledger row - the column balance maths reads (ARCHITECTURE.md C.3 #10).
 * Kept consistent with {@link TransactionType} by DB CHECKs and service validation:
 * INCOME is always INFLOW, EXPENSE is always OUTFLOW; ADJUSTMENT may be either.
 */
public enum TransactionDirection {
    INFLOW,
    OUTFLOW
}
