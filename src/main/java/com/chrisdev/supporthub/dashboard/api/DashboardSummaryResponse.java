package com.chrisdev.supporthub.dashboard.api;


import com.chrisdev.supporthub.asset.domain.AssetStatus;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import com.chrisdev.supporthub.user.api.UserSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Las secciones que no aplican al rol del usuario vienen como null.
 */
public record DashboardSummaryResponse(
        String scope,                    // ALL: ve toda la operación | OWN: solo lo suyo
        Period period,
        TicketMetrics tickets,
        AgentMetrics mine,               // solo ADMIN y AGENT
        AssetMetrics assets,             // solo ADMIN, ASSET_MANAGER y AGENT
        List<ActivityItem> recentActivity,
        Instant generatedAt
) {

    public record Period(Instant from, Instant to, int days) {
    }

    public record TicketMetrics(
            long open,
            long unassigned,
            long highPriorityOpen,
            long createdInPeriod,
            long resolvedInPeriod,
            Double avgResolutionHours,
            Map<TicketStatus, Long> byStatus,
            List<CategoryCount> byCategory
    ) {
    }

    public record CategoryCount(String category, long count) {
    }

    public record AgentMetrics(long assignedOpen, long resolvedInPeriod) {
    }

    public record AssetMetrics(long total, Map<AssetStatus, Long> byStatus, List<TypeCount> byType) {
    }

    public record TypeCount(String type, long count) {
    }

    public record ActivityItem(
            Instant at,
            String eventType,
            UserSummary actor,
            UUID ticketId,
            String ticketCode,
            String ticketTitle,
            String oldValue,
            String newValue,
            String note
    ) {
    }
}