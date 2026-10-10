package com.spendio.notifications.repository;

import com.spendio.notifications.domain.Notification;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    boolean existsByUserIdAndDedupeKeyAndDeletedAtIsNull(Long userId, String dedupeKey);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
              AND n.deletedAt IS NULL
              AND (:includeDismissed = true OR n.dismissedAt IS NULL)
              AND (:unreadOnly = false OR n.readAt IS NULL)
              AND (:type IS NULL OR n.type = :type)
              AND (:severity IS NULL OR n.severity = :severity)
            """)
    Page<Notification> inbox(@Param("userId") Long userId,
            @Param("includeDismissed") boolean includeDismissed,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("type") NotificationType type,
            @Param("severity") NotificationSeverity severity,
            Pageable pageable);

    @Query("""
            SELECT n.type AS type, COUNT(n) AS count FROM Notification n
            WHERE n.userId = :userId
              AND n.readAt IS NULL
              AND n.dismissedAt IS NULL
              AND n.deletedAt IS NULL
            GROUP BY n.type
            """)
    List<NotificationCount> unreadCounts(@Param("userId") Long userId);

    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :readAt
            WHERE n.userId = :userId AND n.readAt IS NULL
              AND n.dismissedAt IS NULL AND n.deletedAt IS NULL
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);

    @Modifying
    @Query("""
            DELETE FROM Notification n
            WHERE (n.readAt IS NOT NULL AND n.readAt < :cutoff)
               OR (n.deletedAt IS NOT NULL AND n.deletedAt < :cutoff)
            """)
    int purgeOlderThan(@Param("cutoff") Instant cutoff);

    interface NotificationCount {
        NotificationType getType();

        long getCount();
    }
}