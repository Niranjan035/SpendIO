package com.spendio.sharedkernel.domain.enums;

/**
 * Account lifecycle states (ARCHITECTURE.md C.3 #8). An ACTIVE account accepts
 * transactions; an ARCHIVED account is read-only but still visible and counted.
 */
public enum AccountStatus {
    ACTIVE,
    ARCHIVED
}