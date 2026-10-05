package com.chrisdev.supporthub.audit.application;

import com.chrisdev.supporthub.audit.api.AuditLogResponse;
import com.chrisdev.supporthub.common.api.PageResponse;
import com.chrisdev.supporthub.user.api.UserSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private final JdbcClient jdbc;

    public AuditQueryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static AuditLogResponse map(ResultSet rs) throws SQLException {
        UUID actorId = rs.getObject("actor_id", UUID.class);
        UserSummary actor = actorId == null ? null : new UserSummary(actorId, rs.getString("full_name"));
        return new AuditLogResponse(
                rs.getObject("id", UUID.class),
                actor,
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("result"),
                AuditJson.read(rs.getString("metadata")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    public PageResponse<AuditLogResponse> search(AuditSearchCriteria c, Pageable pageable) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> params = new HashMap<>();

        if (c.actorId() != null) {
            where.append(" AND a.actor_id = :actorId");
            params.put("actorId", c.actorId());
        }
        if (c.action() != null) {
            where.append(" AND a.action = :action");
            params.put("action", c.action().name());
        }
        if (c.entityType() != null) {
            where.append(" AND a.entity_type = :entityType");
            params.put("entityType", c.entityType().name());
        }
        if (c.entityId() != null) {
            where.append(" AND a.entity_id = :entityId");
            params.put("entityId", c.entityId());
        }
        if (c.result() != null) {
            where.append(" AND a.result = :result");
            params.put("result", c.result().name());
        }
        if (c.from() != null) {
            where.append(" AND a.created_at >= :fromTs");
            params.put("fromTs", c.from().atOffset(ZoneOffset.UTC));
        }
        if (c.to() != null) {
            where.append(" AND a.created_at < :toTs");
            params.put("toTs", c.to().atOffset(ZoneOffset.UTC));
        }

        long total = jdbc.sql("SELECT COUNT(*) FROM audit_logs a" + where)
                .params(params)
                .query(Long.class)
                .single();

        List<AuditLogResponse> content = jdbc.sql("""
                            SELECT a.id, a.actor_id, u.full_name, a.action, a.entity_type, a.entity_id,
                                   a.result, cast(a.metadata as text) as metadata, a.created_at
                            FROM audit_logs a
                            LEFT JOIN users u ON u.id = a.actor_id
                        """ + where + " ORDER BY a.created_at DESC, a.id DESC LIMIT :limit offset :offset")
                .params(params)
                .param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset())
                .query((rs, rowNum) -> map(rs))
                .list();

        int totalPages = (int) Math.ceil((double) total / pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }
}
