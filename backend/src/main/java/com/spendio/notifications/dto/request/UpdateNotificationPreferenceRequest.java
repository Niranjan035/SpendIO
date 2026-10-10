package com.spendio.notifications.dto.request;

import com.spendio.sharedkernel.domain.enums.NotificationChannel;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import jakarta.validation.constraints.NotNull;

/** PUT /notifications/preferences body (ARCHITECTURE.md D.13). */
public record UpdateNotificationPreferenceRequest(
        @NotNull NotificationType type,
        @NotNull NotificationChannel channel,
        boolean enabled,
        NotificationSeverity minSeverity) {
}