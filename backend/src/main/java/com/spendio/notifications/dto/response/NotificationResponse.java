package com.spendio.notifications.dto.response;

import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import java.time.Instant;
import java.util.Map;

/** Inbox row read model (ARCHITECTURE.md D.13). */
public record NotificationResponse(
        Long id,
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String body,
        String entityType,
        Long entityId,
        String actionUrl,
        Map<String, Object> payload,
        Instant readAt,
        Instant createdAt) {
}