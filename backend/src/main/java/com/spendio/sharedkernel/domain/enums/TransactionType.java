package com.spendio.sharedkernel.domain.enums;

/**
 * What a ledger row represents (ARCHITECTURE.md D.8). {@code TRANSFER} rows are
 * excluded from income/expense totals by query filter; {@code ADJUSTMENT} rows
 * reconcile a stated balance and never carry a category (mirrored by DB CHECKs).
 */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    ADJUSTMENT
}
