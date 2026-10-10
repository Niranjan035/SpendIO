package com.spendio.notifications.notifier;

import com.spendio.notifications.service.NotificationService;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.events.PasswordChangedEvent;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raises a security alert when the account password changes (ARCHITECTURE.md D.13,
 * Phase 14). Reacts to the kernel event so the {@code auth} module never depends on
 * {@code notifications} (B.3).
 */
@Component
public class PasswordChangedNotifier {

    private final NotificationService notifications;

    public PasswordChangedNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    @Transactional
    public void on(PasswordChangedEvent event) {
        notifications.create(
                event.userId(),
                NotificationType.SYSTEM,
                NotificationSeverity.HIGH,
                "Password changed",
                "Your password was changed and all other sessions were signed out. If this was not you, reset your password immediately.",
                null,
                null,
                Map.of("changedAt", event.changedAt().toString()),
                "password-changed:" + event.userId() + ":" + event.changedAt());
    }
}
