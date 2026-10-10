package com.spendio.notifications.repository;

import com.spendio.notifications.domain.NotificationPreference;
import com.spendio.sharedkernel.domain.enums.NotificationChannel;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserIdAndTypeAndChannel(Long userId, NotificationType type,
            NotificationChannel channel);

    List<NotificationPreference> findAllByUserIdOrderByTypeAscChannelAsc(Long userId);

    boolean existsByUserId(Long userId);
}