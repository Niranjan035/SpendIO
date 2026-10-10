package com.spendio.auth.domain;

/** Why a session ended (V2 {@code user_sessions.revocation_reason}). */
public enum RevocationReason {
    LOGOUT,
    ROTATED,
    REUSE_DETECTED,
    PASSWORD_CHANGE,
    ADMIN,
    EXPIRED,
    ACCOUNT_DELETED
}
