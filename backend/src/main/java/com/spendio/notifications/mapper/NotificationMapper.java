package com.spendio.notifications.mapper;

import com.spendio.notifications.domain.Notification;
import com.spendio.notifications.dto.response.NotificationResponse;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getSeverity(),
                notification.getTitle(),
                notification.getBody(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getActionUrl(),
                notification.getPayload(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}