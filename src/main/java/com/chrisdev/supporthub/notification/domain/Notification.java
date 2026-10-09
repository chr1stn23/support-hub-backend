package com.chrisdev.supporthub.notification.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationType type;

    @Column(nullable = false, length = 500, updatable = false)
    private String message;

    @Column(name = "ticket_id", updatable = false)
    private UUID ticketId;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "asset_id", updatable = false)
    private UUID assetId;

    protected Notification() {
    }

    public static Notification create(UUID userId, NotificationType type, String message, UUID ticketId, UUID assetId) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.message = message.length() <= 500 ? message : message.substring(0, 497) + "...";
        n.ticketId = ticketId;
        n.assetId = assetId;
        return n;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getAssetId() {
        return assetId;
    }
}
