package com.chrisdev.supporthub.notification.application;

import com.chrisdev.supporthub.common.api.PageResponse;
import com.chrisdev.supporthub.common.exception.ResourceNotFoundException;
import com.chrisdev.supporthub.notification.api.NotificationResponse;
import com.chrisdev.supporthub.notification.domain.Notification;
import com.chrisdev.supporthub.notification.domain.NotificationType;
import com.chrisdev.supporthub.notification.infrastructure.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    private void send(NotificationType type, String message, UUID ticketId, UUID assetId,
            UUID actorId, Collection<UUID> recipients) {
        Set<UUID> targets = recipients.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(actorId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (targets.isEmpty()) {
            return;
        }
        repository.saveAll(targets.stream()
                .map(userId -> Notification.create(userId, type, message, ticketId, assetId))
                .toList());
    }

    /**
     * Notificación sobre un ticket.
     */
    @Transactional
    public void notifyUsers(NotificationType type, String message, UUID ticketId,
            UUID actorId, Collection<UUID> recipients) {
        send(type, message, ticketId, null, actorId, recipients);
    }

    /**
     * Notificación sobre un equipo.
     */
    @Transactional
    public void notifyAboutAsset(NotificationType type, String message, UUID assetId,
            UUID actorId, Collection<UUID> recipients) {
        send(type, message, null, assetId, actorId, recipients);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        // El orden es fijo (más recientes primero): ignoramos el sort que mande el cliente
        Pageable fixed = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> page = unreadOnly
                ? repository.findByUserIdAndReadAtIsNull(userId, fixed)
                : repository.findByUserId(userId, fixed);
        return PageResponse.from(page, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        // 404 también si la notificación existe pero es de otra persona
        Notification n = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        n.markRead();
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, Instant.now());
    }
}
