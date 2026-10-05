package com.chrisdev.supporthub.audit.application;

import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.audit.domain.AuditResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcClient jdbc;

    public AuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registra una acción exitosa DENTRO de la transacción del llamador:
     * si la operación de negocio hace rollback, esta fila también.
     * No debe llamarse desde una transacción readOnly.
     */
    @Transactional
    public void record(UUID actorId, AuditAction action, AuditEntityType entityType,
            UUID entityId, Map<String, Object> metadata) {
        insert(actorId, action, entityType, entityId, AuditResult.SUCCESS, metadata);
    }

    /**
     * Registra un intento fallido en una transacción PROPIA: sobrevive aunque la
     * del llamador haga rollback. Nunca propaga errores: auditar no debe tapar el error original.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID actorId, AuditAction action, AuditEntityType entityType,
            UUID entityId, Map<String, Object> metadata) {
        try {
            insert(actorId, action, entityType, entityId, AuditResult.FAILURE, metadata);
        } catch (RuntimeException ex) {
            log.error("Could not write audit log for action {}", action, ex);
        }
    }

    private void insert(UUID actorId, AuditAction action, AuditEntityType entityType,
            UUID entityId, AuditResult result, Map<String, Object> metadata) {
        jdbc.sql("""
                        INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, result, metadata)
                        VALUES (:actorId, :action, :entityType, :entityId, :result, cast(:metadata as jsonb))
                        """)
                .param("actorId", actorId)
                .param("action", action.name())
                .param("entityType", entityType.name())
                .param("entityId", entityId)
                .param("result", result.name())
                .param("metadata", AuditJson.write(metadata))
                .update();
    }
}
