package com.spendio.sharedkernel.domain.enums;

/**
 * The CSV import state machine (ARCHITECTURE.md D.12): upload -> parse -> map ->
 * validate -> execute. {@code COMPLETED_WITH_ERRORS} and {@code FAILED} are both
 * terminal and keep their rows intact for inspection/retry.
 */
public enum ImportStatus {
    UPLOADED,
    PARSING,
    PARSED,
    MAPPING_REQUIRED,
    VALIDATING,
    READY,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}