package com.chrisdev.supporthub.audit.application;

import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.audit.domain.AuditResult;

import java.time.Instant;
import java.util.UUID;

public record AuditSearchCriteria(
        UUID actorId,
        AuditAction action,
        AuditEntityType entityType,
        UUID entityId,
        AuditResult result,
        Instant from,
        Instant to
) {
}
