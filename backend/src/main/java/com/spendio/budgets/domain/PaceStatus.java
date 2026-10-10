package com.spendio.budgets.domain;

/**
 * Whether spending is ahead of, behind on, or within the straight-line pace of the
 * period, plus the terminal {@code EXCEEDED} state (ARCHITECTURE.md D.10).
 *
 * <p>Boundaries: {@code EXCEEDED} once spent meets the allowance; otherwise pace is
 * compared against expected spend to date with a +/-10% band, so a normal day of
 * shopping never flips the verdict.
 */
public enum PaceStatus {
    UNDER_PACE,
    ON_PACE,
    OVER_PACE,
    EXCEEDED
}
