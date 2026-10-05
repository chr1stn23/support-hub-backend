package com.chrisdev.supporthub.audit.api;

import com.chrisdev.supporthub.user.api.UserSummary;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UserSummary actor,
        String action,
        String entityType,
        UUID entityId,
        String result,
        Map<String, Object> metadata,
        Instant createdAt
) {
}