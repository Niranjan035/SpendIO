package com.spendio.notifications.service;

import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.mail.NotificationMailPort;
import com.spendio.users.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Routes an {@code EMAIL}-preferred notification to {@link NotificationMailPort}
 * (ARCHITECTURE.md D.13). Runs async and never throws - a slow or failing mail
 * delivery must not affect the inbox row or the business transaction that created it.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationMailPort mail;
    private final ProfileService profile;

    public NotificationDispatcher(NotificationMailPort mail, ProfileService profile) {
        this.mail = mail;
        this.profile = profile;
    }

    @Async
    public void email(Long userId, NotificationType type, String subject, String body) {
        try {
            String email = profile.get(userId).email();
            mail.sendNotification(email, subject, body);
        } catch (RuntimeException ex) {
            log.warn("notification email skipped - user={} type={}: {}", userId, type, ex.getMessage());
        }
    }
}