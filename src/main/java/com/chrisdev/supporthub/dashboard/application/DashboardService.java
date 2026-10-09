package com.chrisdev.supporthub.dashboard.application;

import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.dashboard.api.DashboardSummaryResponse;
import com.chrisdev.supporthub.dashboard.api.DashboardSummaryResponse.AgentMetrics;
import com.chrisdev.supporthub.dashboard.api.DashboardSummaryResponse.AssetMetrics;
import com.chrisdev.supporthub.dashboard.api.DashboardSummaryResponse.Period;
import com.chrisdev.supporthub.dashboard.api.DashboardSummaryResponse.TicketMetrics;
import com.chrisdev.supporthub.dashboard.infrastructure.DashboardQueryRepository;
import com.chrisdev.supporthub.dashboard.infrastructure.DashboardQueryRepository.TicketCounters;
import com.chrisdev.supporthub.ticket.application.TicketAccessPolicy;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * REPEATABLE READ: todas las consultas leen la MISMA foto de la base de datos, así que las
 * cifras del dashboard son coherentes entre sí aunque otros usuarios estén escribiendo.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DashboardService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;
    private static final int ACTIVITY_LIMIT = 10;

    private final DashboardQueryRepository queries;
    private final TicketAccessPolicy accessPolicy;

    public DashboardService(DashboardQueryRepository queries, TicketAccessPolicy accessPolicy) {
        this.queries = queries;
        this.accessPolicy = accessPolicy;
    }

    private static Double round1(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }

    public DashboardSummaryResponse summary(int days, AuthenticatedUser user) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The 'days' parameter must be between %d and %d.".formatted(MIN_DAYS, MAX_DAYS));
        }

        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(days));

        boolean seesAll = accessPolicy.canViewAll(user);
        UUID scope = seesAll ? null : user.id();

        TicketCounters c = queries.ticketCounters(since, scope);
        TicketMetrics tickets = new TicketMetrics(
                c.open(), c.unassigned(), c.highPriorityOpen(), c.createdInPeriod(), c.resolvedInPeriod(),
                round1(c.avgResolutionHours()),
                queries.ticketsByStatus(scope),
                queries.ticketsByCategory(since, scope),
                queries.ticketTrend(since, now, scope));

        AgentMetrics mine = seesAll ? queries.agentMetrics(user.id(), since) : null;
        AssetMetrics assets = canSeeAssets(user) ? assetMetrics() : null;

        return new DashboardSummaryResponse(
                seesAll ? "ALL" : "OWN",
                new Period(since, now, days),
                tickets,
                mine,
                assets,
                queries.recentActivity(scope, ACTIVITY_LIMIT),
                now);
    }

    private AssetMetrics assetMetrics() {
        var byStatus = queries.assetsByStatus();
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new AssetMetrics(total, byStatus, queries.assetsByType());
    }

    private boolean canSeeAssets(AuthenticatedUser user) {
        return user.hasRole(RoleName.ADMIN) || user.hasRole(RoleName.ASSET_MANAGER) || user.hasRole(RoleName.AGENT);
    }
}
