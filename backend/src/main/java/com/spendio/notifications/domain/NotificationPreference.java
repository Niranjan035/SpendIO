package com.spendio.notifications.domain;

import com.spendio.sharedkernel.domain.AuditableEntity;
import com.spendio.sharedkernel.domain.enums.NotificationChannel;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Per-type, per-channel preference (ARCHITECTURE.md D.13): {@code enabled} and the
 * {@code minSeverity} floor. A disabled channel suppresses creation entirely; a
 * preference row with {@code minSeverity} set filters low-severity noise.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference extends AuditableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_severity")
    private NotificationSeverity minSeverity;

    protected NotificationPreference() {
    }

    public static NotificationPreference create(Long userId, NotificationType type, NotificationChannel channel,
            boolean enabled, NotificationSeverity minSeverity) {
        NotificationPreference preference = new NotificationPreference();
        preference.userId = userId;
        preference.type = type;
        preference.channel = channel;
        preference.enabled = enabled;
        preference.minSeverity = minSeverity;
        return preference;
    }

    public void update(boolean enabled, NotificationSeverity minSeverity) {
        this.enabled = enabled;
        this.minSeverity = minSeverity;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public NotificationSeverity getMinSeverity() {
        return minSeverity;
    }
}