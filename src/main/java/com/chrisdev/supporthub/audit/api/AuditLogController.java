package com.chrisdev.supporthub.audit.api;

import com.chrisdev.supporthub.audit.application.AuditQueryService;
import com.chrisdev.supporthub.audit.application.AuditSearchCriteria;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.audit.domain.AuditResult;
import com.chrisdev.supporthub.common.api.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditQueryService queryService;

    public AuditLogController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Siempre ordenado del más reciente al más antiguo; el parámetro sort se ignora.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) AuditResult result,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {

        var criteria = new AuditSearchCriteria(actorId, action, entityType, entityId, result, from, to);
        return queryService.search(criteria, pageable);
    }

}
