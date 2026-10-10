package com.spendio.notifications.service;

import com.spendio.notifications.domain.Notification;
import com.spendio.notifications.dto.response.NotificationResponse;
import com.spendio.notifications.dto.response.UnreadCountResponse;
import com.spendio.notifications.mapper.NotificationMapper;
import com.spendio.notifications.repository.NotificationRepository;
import com.spendio.notifications.support.NotificationStreamHub;
import com.spendio.sharedkernel.domain.enums.NotificationChannel;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.web.PageRequestFactory;
import com.spendio.sharedkernel.web.PageResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The single notification write path (ARCHITECTURE.md D.13): a submitted event is
 * gated by the per-type/channel preference (a disabled channel means no row is
 * created), deduplicated on {@code dedupe_key} and delivered to connected SSE
 * clients after the surrounding transaction commits.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Severity ordering: higher index = more severe. */
    private static final List<NotificationSeverity> SEVERITY_ORDER = List.of(
            NotificationSeverity.INFO, NotificationSeverity.LOW, NotificationSeverity.MEDIUM,
            NotificationSeverity.HIGH, NotificationSeverity.CRITICAL);

    private final NotificationRepository notifications;
    private final NotificationPreferenceService preferences;
    private final NotificationDispatcher dispatcher;
    private final NotificationMapper mapper;
    private final NotificationStreamHub stream;

    public NotificationService(NotificationRepository notifications,
            NotificationPreferenceService preferences, NotificationDispatcher dispatcher,
            NotificationMapper mapper, NotificationStreamHub stream) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.dispatcher = dispatcher;
        this.mapper = mapper;
        this.stream = stream;
    }

    /**
     * Create one notification, honouring preferences and the dedupe index. Returns
     * empty when the channel is disabled, the severity is below the preference floor,
     * or the same {@code dedupe_key} already produced a row - either way the source
     * event stays silent and never fails the business transaction.
     */
    @Transactional
    public Optional<Notification> create(Long userId, NotificationType type, NotificationSeverity severity,
            String title, String body, EntityRef entity, String actionUrl, Map<String, Object> payload,
            String dedupeKey) {
        if (!inAppAllowed(userId, type, severity)) {
            log.debug("notification suppressed by preference: user={} type={} severity={}", userId, type, severity);
            return Optional.empty();
        }
        if (dedupeKey != null && !dedupeKey.isBlank()
                && notifications.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(userId, dedupeKey)) {
            log.debug("notification deduplicated: user={} type={} key={}", userId, type, dedupeKey);
            return Optional.empty();
        }
        if (entity != null) {
            if (entity.entityType() == null && entity.entityId() == null) {
                entity = null;
            }
        }
        Notification notification = Notification.create(
                userId, type, severity, title.trim(), body, entity == null ? null : entity.entityType(),
                entity == null ? null : entity.entityId(), actionUrl, payload, blankToNull(dedupeKey));
        Notification saved = notifications.saveAndFlush(notification);

        boolean emailEnabled = emailAllowed(userId, type, severity);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stream.publish(userId, mapper.toResponse(saved));
                    if (emailEnabled) {
                        dispatcher.email(userId, type, saved.getTitle(), saved.getBody());
                    }
                }
            });
        } else {
            stream.publish(userId, mapper.toResponse(saved));
            if (emailEnabled) {
                dispatcher.email(userId, type, saved.getTitle(), saved.getBody());
            }
        }
        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, NotificationType type,
            NotificationSeverity severity, Integer page, Integer size) {
        Pageable pageable = PageRequestFactory.of(page, size);
        Page<Notification> result = notifications.inbox(userId, false, unreadOnly, type, severity, pageable);
        return PageResponse.from(result.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        Map<NotificationType, Integer> byType = new EnumMap<>(NotificationType.class);
        int total = 0;
        for (NotificationRepository.NotificationCount count : notifications.unreadCounts(userId)) {
            byType.put(count.getType(), (int) count.getCount());
            total += count.getCount();
        }
        return new UnreadCountResponse(total, byType);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long id) {
        Notification notification = findOwned(userId, id);
        if (notification.getReadAt() != null) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ALREADY_READ,
                    "Notification is already read").withProperty("id", id);
        }
        notification.markRead(Instant.now());
        return mapper.toResponse(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId, Instant.now());
    }

    @Transactional
    public void dismiss(Long userId, Long id) {
        Notification notification = findOwned(userId, id);
        if (notification.getDismissedAt() == null) {
            notification.dismiss(Instant.now());
        }
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Notification notification = findOwned(userId, id);
        notification.softDelete();
    }

    private Notification findOwned(Long userId, Long id) {
        return notifications.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND,
                        "Notification not found").withProperty("id", id));
    }

    private boolean inAppAllowed(Long userId, NotificationType type, NotificationSeverity severity) {
        return allowed(userId, type, NotificationChannel.IN_APP, severity, true);
    }

    private boolean emailAllowed(Long userId, NotificationType type, NotificationSeverity severity) {
        return allowed(userId, type, NotificationChannel.EMAIL, severity, false);
    }

    private boolean allowed(Long userId, NotificationType type, NotificationChannel channel,
            NotificationSeverity severity, boolean defaultEnabled) {
        Optional<com.spendio.notifications.domain.NotificationPreference> preference =
                preferences.find(userId, type, channel);
        boolean enabled = preference.map(com.spendio.notifications.domain.NotificationPreference::isEnabled)
                .orElse(defaultEnabled);
        if (!enabled) {
            return false;
        }
        NotificationSeverity min = preference.map(
                com.spendio.notifications.domain.NotificationPreference::getMinSeverity).orElse(null);
        return min == null || SEVERITY_ORDER.indexOf(severity) >= SEVERITY_ORDER.indexOf(min);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    /** Deep-link target: an owning entity plus the stable route that shows it. */
    public record EntityRef(String entityType, Long entityId) {
    }

    /** Comparator kept for symmetric severity checks exported to tests. */
    public static int compareSeverity(NotificationSeverity a, NotificationSeverity b) {
        return Integer.compare(SEVERITY_ORDER.indexOf(a), SEVERITY_ORDER.indexOf(b));
    }
}