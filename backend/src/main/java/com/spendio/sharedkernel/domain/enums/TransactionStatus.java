package com.spendio.sharedkernel.domain.enums;

/**
 * Posting state of a ledger row. VOID rows are excluded from balance maths and
 * category reports; a VOID row can never carry a category (DB CHECK).
 */
public enum TransactionStatus {
    POSTED,
    PENDING,
    VOID
}
