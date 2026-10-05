package com.chrisdev.supporthub.notification.api;

import com.chrisdev.supporthub.notification.domain.Notification;
import com.chrisdev.supporthub.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String message,
        UUID ticketId,
        boolean read,
        Instant readAt,
        Instant createdAt
) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getTicketId(),
                n.getReadAt() != null, n.getReadAt(), n.getCreatedAt());
    }
}