package com.spendio.notifications.notifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.spendio.notifications.service.NotificationService;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.events.PasswordChangedEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Security alert on password change (ARCHITECTURE.md D.13, Phase 14). */
@ExtendWith(MockitoExtension.class)
class PasswordChangedNotifierTest {

    @Mock
    private NotificationService notifications;

    private PasswordChangedNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new PasswordChangedNotifier(notifications);
    }

    @Test
    void raisesAHighSeveritySystemAlertForTheOwner() {
        Instant changedAt = Instant.parse("2026-02-01T09:00:00Z");

        notifier.on(new PasswordChangedEvent(7L, changedAt));

        ArgumentCaptor<String> dedupe = ArgumentCaptor.forClass(String.class);
        verify(notifications).create(
                eq(7L),
                eq(NotificationType.SYSTEM),
                eq(NotificationSeverity.HIGH),
                any(),
                any(),
                any(),
                any(),
                any(),
                dedupe.capture());
        assertThat(dedupe.getValue()).contains("password-changed:7");
    }
}
