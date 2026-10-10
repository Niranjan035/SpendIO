package com.spendio.notifications.domain;

import com.spendio.sharedkernel.domain.SoftDeletableEntity;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One inbox row (ARCHITECTURE.md D.13): the deep link to the affected entity plus a
 * payload the notifier attached. Read/dismiss/delete state lives here and
 * {@code dedupe_key} is the idempotency gate behind "one notification per event".
 */
@Entity
@Table(name = "notifications")
public class Notification extends SoftDeletableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private NotificationSeverity severity;

    @Column(name = "title", length = 160, nullable = false)
    private String title;

    @Column(name = "body", length = 2000, nullable = false)
    private String body;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object> payload;

    @Column(name = "dedupe_key", length = 255)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    protected Notification() {
    }

    public static Notification create(Long userId, NotificationType type, NotificationSeverity severity,
            String title, String body, String entityType, Long entityId, String actionUrl,
            Map<String, Object> payload, String dedupeKey) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.severity = severity;
        notification.title = title;
        notification.body = body;
        notification.entityType = entityType;
        notification.entityId = entityId;
        notification.actionUrl = actionUrl;
        notification.payload = payload;
        notification.dedupeKey = dedupeKey;
        return notification;
    }

    /** Marks read; {@code NOTIFICATION_ALREADY_READ} is thrown by the service. */
    public void markRead(Instant at) {
        this.readAt = at;
    }

    public void dismiss(Instant at) {
        this.dismissedAt = at;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getDismissedAt() {
        return dismissedAt;
    }
}