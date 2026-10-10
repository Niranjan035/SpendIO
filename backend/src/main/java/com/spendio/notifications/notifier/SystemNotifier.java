package com.spendio.notifications.notifier;

import com.spendio.notifications.service.NotificationPreferenceService;
import com.spendio.notifications.service.NotificationService;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.events.NewUserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * System events (ARCHITECTURE.md D.13, K.10): the registration handler bootstraps the
 * preference matrix and drops the welcome notification. Other system alerts reuse
 * {@link #send} under a caller-supplied dedupe key.
 */
@Component
public class SystemNotifier {

    private final NotificationService notifications;
    private final NotificationPreferenceService preferences;

    public SystemNotifier(NotificationService notifications, NotificationPreferenceService preferences) {
        this.notifications = notifications;
        this.preferences = preferences;
    }

    @EventListener
    @Transactional
    public void onRegistration(NewUserRegisteredEvent event) {
        preferences.ensureDefaults(event.userId());
        send(event.userId(), NotificationSeverity.INFO, "Welcome to SpendIO",
                "Hi " + event.displayName() + "! We will keep you posted as your budgets, goals "
                        + "and recurring transactions change.",
                null, null, null, "welcome:" + event.userId());
    }

    /** Generic system-notification entry point for later phases (security, maintenance). */
    public void send(Long userId, NotificationSeverity severity, String title, String body,
            NotificationService.EntityRef entity, String actionUrl, java.util.Map<String, Object> payload,
            String dedupeKey) {
        notifications.create(userId, NotificationType.SYSTEM, severity, title, body, entity, actionUrl,
                payload, dedupeKey);
    }
}