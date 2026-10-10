package com.spendio.sharedkernel.domain.enums;

/**
 * How a goal contribution entered the system (ARCHITECTURE.md C.3 #17):
 * {@code TRANSFER} optionally moves real money via a paired ledger transfer,
 * {@code ADJUSTMENT} is a bookkeeping correction, {@code MANUAL} is a plain deposit
 * or withdrawal.
 */
public enum GoalContributionMethod {
    MANUAL,
    TRANSFER,
    ADJUSTMENT
}
