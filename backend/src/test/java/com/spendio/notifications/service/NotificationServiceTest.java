package com.spendio.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.notifications.domain.Notification;
import com.spendio.notifications.domain.NotificationPreference;
import com.spendio.notifications.dto.response.NotificationResponse;
import com.spendio.notifications.mapper.NotificationMapper;
import com.spendio.notifications.repository.NotificationRepository;
import com.spendio.notifications.support.NotificationStreamHub;
import com.spendio.sharedkernel.domain.enums.NotificationChannel;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.error.BusinessException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The notification write gate (ARCHITECTURE.md D.13/K.10): preferences decide whether
 * a row is created at all, the dedupe key makes a replayed event land exactly once,
 * and read state persists per row.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final long USER = 7L;

    @Mock
    private NotificationRepository notifications;
    @Mock
    private NotificationPreferenceService preferences;
    @Mock
    private NotificationDispatcher dispatcher;
    @Mock
    private NotificationMapper mapper;
    @Mock
    private NotificationStreamHub stream;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, preferences, dispatcher, mapper, stream);
    }

    @Test
    void disabledChannelProducesNoRow() {
        when(preferences.find(USER, NotificationType.BUDGET_ALERT, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(preference(false, null)));

        Optional<Notification> result = service.create(USER, NotificationType.BUDGET_ALERT,
                NotificationSeverity.HIGH, "t", "b", null, null, null, "budget:1:2026-01-01:90");

        assertThat(result).isEmpty();
        verify(notifications, never()).saveAndFlush(any());
    }

    @Test
    void minSeverityFloorFiltersLowSeverity() {
        when(preferences.find(USER, NotificationType.SYSTEM, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(preference(true, NotificationSeverity.MEDIUM)));

        assertThat(service.create(USER, NotificationType.SYSTEM, NotificationSeverity.LOW,
                "t", "b", null, null, null, "k1")).isEmpty();

        when(notifications.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.create(USER, NotificationType.SYSTEM, NotificationSeverity.HIGH,
                "t", "b", null, null, null, "k2")).isPresent();
    }

    @Test
    void dedupeKeySwallowsReplayedEvents() {
        when(preferences.find(USER, NotificationType.BUDGET_ALERT, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(preference(true, null)));
        when(notifications.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(USER, "budget:1:2026-01-01:90"))
                .thenReturn(true);

        Optional<Notification> result = service.create(USER, NotificationType.BUDGET_ALERT,
                NotificationSeverity.HIGH, "t", "b", null, null, null, "budget:1:2026-01-01:90");

        assertThat(result).isEmpty();
        verify(notifications, never()).saveAndFlush(any());
    }

    @Test
    void createSavesAndPublishesToTheStream() {
        when(preferences.find(USER, NotificationType.BUDGET_ALERT, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(preference(true, null)));
        when(notifications.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Notification.class)))
                .thenReturn(new NotificationResponse(1L, NotificationType.BUDGET_ALERT,
                        NotificationSeverity.HIGH, "t", "b", "BUDGET", 1L, "/budgets/1", null, null, Instant.now()));

        Optional<Notification> result = service.create(USER, NotificationType.BUDGET_ALERT,
                NotificationSeverity.HIGH, "t", "b",
                new NotificationService.EntityRef("BUDGET", 1L), "/budgets/1", null,
                "budget:1:2026-01-01:90");

        assertThat(result).isPresent().get().extracting(Notification::getType)
                .isEqualTo(NotificationType.BUDGET_ALERT);
        verify(notifications).saveAndFlush(any());
        verify(stream).publish(org.mockito.ArgumentMatchers.eq(USER), any(Object.class));
        verify(dispatcher, never()).email(any(), any(), any(), any());
    }

    @Test
    void markingAnAlreadyReadNotificationThrows() {
        Notification read = Notification.create(USER, NotificationType.SYSTEM,
                NotificationSeverity.INFO, "t", "b", null, null, null, null, null);
        read.markRead(Instant.now());
        when(notifications.findByIdAndUserIdAndDeletedAtIsNull(1L, USER)).thenReturn(Optional.of(read));

        assertThatThrownBy(() -> service.markRead(USER, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already read");
    }

    private static NotificationPreference preference(boolean enabled, NotificationSeverity minSeverity) {
        return NotificationPreference.create(USER, NotificationType.BUDGET_ALERT, NotificationChannel.IN_APP,
                enabled, minSeverity);
    }
}