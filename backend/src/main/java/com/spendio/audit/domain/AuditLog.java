package com.spendio.audit.domain;

import com.spendio.sharedkernel.domain.enums.AuditActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One append-only audit row (ARCHITECTURE.md C.3 #22, D.15).
 *
 * <p>Never stores secrets: before/after JSON is built by the caller from the fields
 * that changed and excludes password hashes and tokens. The row has no version column
 * because it is never updated.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 8)
    private AuditActorType actorType;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json")
    private Map<String, Object> beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json")
    private Map<String, Object> afterJson;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "trace_id", length = 32)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public static AuditLog of(Long userId, AuditActorType actorType, String action, String entityType,
            Long entityId, Map<String, Object> beforeJson, Map<String, Object> afterJson,
            String ipAddress, String userAgent, String traceId, Instant createdAt) {
        AuditLog row = new AuditLog();
        row.userId = userId;
        row.actorType = actorType;
        row.action = action;
        row.entityType = entityType;
        row.entityId = entityId;
        row.beforeJson = beforeJson;
        row.afterJson = afterJson;
        row.ipAddress = ipAddress;
        row.userAgent = userAgent;
        row.traceId = traceId;
        row.createdAt = createdAt;
        return row;
    }

    /** Detaches the row from the user before a hard purge (C.4: audit survives deletion). */
    public void anonymise() {
        this.userId = null;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public AuditActorType getActorType() {
        return actorType;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Map<String, Object> getBeforeJson() {
        return beforeJson;
    }

    public Map<String, Object> getAfterJson() {
        return afterJson;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
