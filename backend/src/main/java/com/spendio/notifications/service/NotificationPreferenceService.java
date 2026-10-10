package com.spendio.notifications.service;

import com.spendio.notifications.domain.NotificationPreference;
import com.spendio.notifications.dto.request.UpdateNotificationPreferenceRequest;
import com.spendio.notifications.dto.response.NotificationPreferenceResponse;
import com.spendio.notifications.repository.NotificationPreferenceRepository;
import com.spendio.sharedkernel.domain.enums.NotificationChannel;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-type, per-channel preferences (ARCHITECTURE.md D.13): read, update and the
 * defaults a brand-new account gets at registration. Users registered before Phase 11
 * fall back to the {@code NotificationService} defaults until they touch a setting.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferences;

    public NotificationPreferenceService(NotificationPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public Optional<NotificationPreference> find(Long userId, NotificationType type, NotificationChannel channel) {
        return preferences.findByUserIdAndTypeAndChannel(userId, type, channel);
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse list(Long userId) {
        List<NotificationPreferenceResponse.Preference> result = preferences
                .findAllByUserIdOrderByTypeAscChannelAsc(userId)
                .stream()
                .map(p -> new NotificationPreferenceResponse.Preference(
                        p.getType(), p.getChannel(), p.isEnabled(), p.getMinSeverity()))
                .toList();
        return new NotificationPreferenceResponse(result);
    }

    @Transactional
    public NotificationPreferenceResponse update(Long userId, UpdateNotificationPreferenceRequest request) {
        NotificationPreference preference = preferences.findByUserIdAndTypeAndChannel(
                userId, request.type(), request.channel())
                .orElseGet(() -> Preferences.defaultFor(userId, request.type(), request.channel()));
        preference.update(request.enabled(), request.minSeverity());
        preferences.save(preference);
        return list(userId);
    }

    /** Idempotent: inserts the full preference matrix once per user. */
    @Transactional
    public void ensureDefaults(Long userId) {
        if (preferences.existsByUserId(userId)) {
            return;
        }
        List<NotificationPreference> defaults = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            defaults.add(Preferences.defaultFor(userId, type, NotificationChannel.IN_APP));
        }
        // Email stays opt-in: quiet by default until a user turns it on.
        for (NotificationType type : NotificationType.values()) {
            defaults.add(NotificationPreference.create(
                    userId, type, NotificationChannel.EMAIL, false, NotificationSeverity.MEDIUM));
        }
        preferences.saveAll(defaults);
    }

    /** Shared default matrix. */
    static final class Preferences {
        private Preferences() {
        }

        static NotificationPreference defaultFor(Long userId, NotificationType type, NotificationChannel channel) {
            if (channel == NotificationChannel.EMAIL) {
                return NotificationPreference.create(userId, type, channel, false, NotificationSeverity.MEDIUM);
            }
            return NotificationPreference.create(userId, type, channel, true, null);
        }
    }
}