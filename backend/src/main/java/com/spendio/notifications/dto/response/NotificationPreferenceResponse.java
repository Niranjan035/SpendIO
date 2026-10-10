package com.spendio.notifications.dto.response;

import com.spendio.sharedkernel.domain.enums.NotificationChannel;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import java.util.List;

/** Per-type, per-channel preference view (ARCHITECTURE.md D.13). */
public record NotificationPreferenceResponse(List<Preference> preferences) {

    public record Preference(
            NotificationType type,
            NotificationChannel channel,
            boolean enabled,
            NotificationSeverity minSeverity) {
    }
}