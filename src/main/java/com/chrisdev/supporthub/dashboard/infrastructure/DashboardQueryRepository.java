package com.chrisdev.supporthub.dashboard.infrastructure;

import com.chrisdev.supporthub.asset.domain.AssetStatus;
import com.chrisdev.supporthub.dashboard.api.DashboardSummaryResponse.*;
import com.chrisdev.supporthub.ticket.domain.TicketPriority;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import com.chrisdev.supporthub.user.api.UserSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class DashboardQueryRepository {

    private static final String OPEN_STATUSES =
            quoted(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING);
    private static final String HIGH_PRIORITIES = quoted(TicketPriority.HIGH, TicketPriority.CRITICAL);

    private final JdbcClient jdbc;

    public DashboardQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String scopeClause(UUID requesterScope) {
        return requesterScope == null ? "" : " AND t.requester_id = :scope";
    }

    // Helpers

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String quoted(Enum<?>... values) {
        return Arrays.stream(values).map(v -> "'" + v.name() + "'").collect(Collectors.joining(", "));
    }

    private JdbcClient.StatementSpec bind(String sql, UUID requesterScope) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        return requesterScope == null ? spec : spec.param("scope", requesterScope);
    }

    // Tickets

    /**
     * Todos los contadores en UNA pasada sobre la tabla (count ... FILTER).
     */
    public TicketCounters ticketCounters(Instant since, UUID requesterScope) {
        String sql = """
                SELECT
                    COUNT(*) FILTER (WHERE t.status IN (%1$s))                                  AS open_total,
                    COUNT(*) FILTER (WHERE t.status = 'OPEN' AND t.agent_id IS NULL)            AS unassigned,
                    COUNT(*) FILTER (WHERE t.status IN (%1$s) AND t.priority in (%2$s))         AS high_priority,
                    COUNT(*) FILTER (WHERE t.created_at >= :since)                              AS created_in_period,
                    COUNT(*) FILTER (WHERE t.resolved_at >= :since)                             AS resolved_in_period,
                    (AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)))
                        FILTER (WHERE t.resolved_at >= :since) / 3600.0)::double precision      AS avg_resolution_hours
                FROM tickets t
                WHERE 1 = 1
                """.formatted(OPEN_STATUSES, HIGH_PRIORITIES) + scopeClause(requesterScope);

        return bind(sql, requesterScope)
                .param("since", utc(since))
                .query((rs, n) -> new TicketCounters(
                        rs.getLong("open_total"),
                        rs.getLong("unassigned"),
                        rs.getLong("high_priority"),
                        rs.getLong("created_in_period"),
                        rs.getLong("resolved_in_period"),
                        rs.getObject("avg_resolution_hours", Double.class)))
                .single();
    }

    public Map<TicketStatus, Long> ticketsByStatus(UUID requesterScope) {
        Map<TicketStatus, Long> result = new EnumMap<>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) {
            result.put(status, 0L); // ceros explícitos: categorías estables para los gráficos
        }
        bind("SELECT t.status, COUNT(*) AS total FROM tickets t WHERE 1 = 1"
                + scopeClause(requesterScope) + " GROUP BY t.status", requesterScope)
                .query((rs, n) -> Map.entry(TicketStatus.valueOf(rs.getString("status")), rs.getLong("total")))
                .list()
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    public List<CategoryCount> ticketsByCategory(Instant since, UUID requesterScope) {
        String sql = """
                SELECT c.name AS category, count(*) AS total
                FROM tickets t
                JOIN categories c ON c.id = t.category_id
                WHERE t.created_at >= :since
                """ + scopeClause(requesterScope) + " GROUP BY c.name ORDER BY total DESC, c.name";

        return bind(sql, requesterScope)
                .param("since", utc(since))
                .query((rs, n) -> new CategoryCount(rs.getString("category"), rs.getLong("total")))
                .list();
    }

    public AgentMetrics agentMetrics(UUID agentId, Instant since) {
        return jdbc.sql("""
                        SELECT
                            COUNT(*) FILTER (WHERE t.status IN ('IN_PROGRESS', 'WAITING')) AS assigned_open,
                            COUNT(*) FILTER (WHERE t.resolved_at >= :since)                AS resolved_in_period
                        FROM tickets t
                        WHERE t.agent_id = :agentId
                        """)
                .param("agentId", agentId)
                .param("since", utc(since))
                .query((rs, n) -> new AgentMetrics(rs.getLong("assigned_open"), rs.getLong("resolved_in_period")))
                .single();
    }

    public List<ActivityItem> recentActivity(UUID requesterScope, int limit) {
        String sql = """
                SELECT h.created_at, h.event_type, h.old_value, h.new_value, h.note,
                    u.id AS actor_id, u.full_name AS actor_name,
                    t.id AS ticket_id, t.ticket_code, t.title
                FROM ticket_history h
                JOIN tickets t ON t.id = h.ticket_id
                JOIN users u ON u.id = h.actor_id
                WHERE 1 = 1
                """ + scopeClause(requesterScope) + " ORDER BY h.created_at DESC, h.id DESC LIMIT :limit";

        return bind(sql, requesterScope)
                .param("limit", limit)
                .query((rs, n) -> new ActivityItem(
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getString("event_type"),
                        new UserSummary(rs.getObject("actor_id", UUID.class), rs.getString("actor_name")),
                        rs.getObject("ticket_id", UUID.class),
                        rs.getString("ticket_code"),
                        rs.getString("title"),
                        rs.getString("old_value"),
                        rs.getString("new_value"),
                        rs.getString("note")
                ))
                .list();
    }

    public List<DayPoint> ticketTrend(Instant since, Instant until, UUID requesterScope) {

        String sql = """
                WITH days AS (
                  SELECT CAST(g AS date) AS day
                  FROM generate_series(CAST(:fromDay AS timestamp), CAST(:toDay AS timestamp), INTERVAL '1 day') AS g
                ),
                created AS (
                  SELECT CAST(t.created_at AT TIME ZONE 'UTC' AS date) AS day, COUNT(*) AS n
                  FROM tickets t
                  WHERE t.created_at >= :since %1$s
                  GROUP BY 1
                ),
                resolved AS (
                  SELECT CAST(t.resolved_at AT TIME ZONE 'UTC' AS date) AS day, COUNT(*) AS n
                  FROM tickets t
                  WHERE t.resolved_at >= :since %1$s
                  GROUP BY 1
                )
                SELECT d.day, COALESCE(c.n, 0) AS created, COALESCE(r.n, 0) AS resolved
                FROM days d
                LEFT JOIN created c ON c.day = d.day
                LEFT JOIN resolved r ON r.day = d.day
                ORDER BY d.day
                """.formatted(scopeClause(requesterScope));

        return bind(sql, requesterScope)
                .param("since", utc(since))
                .param("fromDay", since.atZone(ZoneOffset.UTC).toLocalDate())
                .param("toDay", until.atZone(ZoneOffset.UTC).toLocalDate())
                .query((rs, n) -> new DayPoint(
                        rs.getObject("day", LocalDate.class).toString(),
                        rs.getLong("created"),
                        rs.getLong("resolved")))
                .list();
    }

    // Activos

    public Map<AssetStatus, Long> assetsByStatus() {
        Map<AssetStatus, Long> result = new EnumMap<>(AssetStatus.class);
        for (AssetStatus status : AssetStatus.values()) {
            result.put(status, 0L);
        }
        jdbc.sql("SELECT a.status, COUNT(*) AS total FROM assets a GROUP BY a.status")
                .query((rs, n) -> Map.entry(AssetStatus.valueOf(rs.getString("status")), rs.getLong("total")))
                .list()
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    /**
     * Activos en circulación (sin los dados de baja), por tipo.
     */
    public List<TypeCount> assetsByType() {
        return jdbc.sql("""
                        SELECT ty.name AS type, count(*) AS total
                        FROM assets a
                        JOIN asset_types ty ON ty.id = a.asset_type_id
                        WHERE a.status <> 'RETIRED'
                        GROUP BY ty.name
                        ORDER BY total DESC, ty.name
                        """)
                .query((rs, n) -> new TypeCount(rs.getString("type"), rs.getLong("total")))
                .list();
    }

    public record TicketCounters(long open, long unassigned, long highPriorityOpen,
                                 long createdInPeriod, long resolvedInPeriod, Double avgResolutionHours) {
    }

}
