package com.spendio.audit.dto;

import com.spendio.audit.domain.AuditLog;
import com.spendio.sharedkernel.domain.enums.AuditActorType;
import java.time.Instant;
import java.util.Map;

/**
 * Read model for the audit-log viewer (ARCHITECTURE.md E.3, Phase 14). Exposes the
 * action and the entity it touched; before/after snapshots are already free of
 * secrets because the writers never store them.
 */
public record AuditLogResponse(
        Long id,
        AuditActorType actorType,
        String action,
        String entityType,
        Long entityId,
        String ipAddress,
        String traceId,
        Map<String, Object> before,
        Map<String, Object> after,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog row) {
        return new AuditLogResponse(
                row.getId(),
                row.getActorType(),
                row.getAction(),
                row.getEntityType(),
                row.getEntityId(),
                row.getIpAddress(),
                row.getTraceId(),
                row.getBeforeJson(),
                row.getAfterJson(),
                row.getCreatedAt());
    }
}
