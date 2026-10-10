package com.spendio.sharedkernel.domain.enums;

/** Per-row pipeline state for a CSV import (ARCHITECTURE.md D.12). */
public enum ImportRowStatus {
    PENDING,
    VALID,
    INVALID,
    DUPLICATE,
    CONFIRMED,
    SKIPPED,
    CREATED,
    FAILED,
    UNDONE
}