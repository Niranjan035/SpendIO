package com.spendio.notifications.dto.response;

import com.spendio.sharedkernel.domain.enums.NotificationType;
import java.util.Map;

/** GET /notifications/unread-count (ARCHITECTURE.md D.13). */
public record UnreadCountResponse(int total, Map<NotificationType, Integer> byType) {
}