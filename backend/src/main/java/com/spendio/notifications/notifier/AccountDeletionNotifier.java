package com.spendio.notifications.notifier;

import com.spendio.notifications.service.NotificationService;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.events.UserAccountDeletedEvent;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drops a system alert when an account is deleted (ARCHITECTURE.md D.13, Phase 14).
 * Reacts to the kernel event so the {@code users} module never depends on
 * {@code notifications} (B.3).
 */
@Component
public class AccountDeletionNotifier {

    private final NotificationService notifications;

    public AccountDeletionNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    @Transactional
    public void on(UserAccountDeletedEvent event) {
        notifications.create(
                event.userId(),
                NotificationType.SYSTEM,
                NotificationSeverity.CRITICAL,
                "Account scheduled for deletion",
                "Your account has been deactivated and will be permanently removed after the grace period.",
                null,
                null,
                java.util.Map.of("deletedAt", event.deletedAt().toString()),
                "account-deleted:" + event.userId());
    }
}
