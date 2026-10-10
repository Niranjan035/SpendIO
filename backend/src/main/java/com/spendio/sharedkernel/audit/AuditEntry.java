package com.spendio.sharedkernel.audit;

import com.spendio.sharedkernel.domain.enums.AuditActorType;
import java.util.Map;

/**
 * A single audit record to append (ARCHITECTURE.md D.15). Uses plain values only so
 * any module can build one without depending on the {@code audit} module (B.3).
 *
 * <p>{@code before}/{@code after} must never contain secrets: services pass only the
 * fields that changed and never password hashes or tokens.
 */
public record AuditEntry(
        Long userId,
        AuditActorType actorType,
        String action,
        String entityType,
        Long entityId,
        Map<String, Object> before,
        Map<String, Object> after) {

    public static AuditEntry user(Long userId, String action, String entityType, Long entityId) {
        return new AuditEntry(userId, AuditActorType.USER, action, entityType, entityId, null, null);
    }

    public static AuditEntry user(Long userId, String action, String entityType, Long entityId,
            Map<String, Object> before, Map<String, Object> after) {
        return new AuditEntry(userId, AuditActorType.USER, action, entityType, entityId, before, after);
    }

    public static AuditEntry system(String action, String entityType, Long entityId) {
        return new AuditEntry(null, AuditActorType.SYSTEM, action, entityType, entityId, null, null);
    }
}
