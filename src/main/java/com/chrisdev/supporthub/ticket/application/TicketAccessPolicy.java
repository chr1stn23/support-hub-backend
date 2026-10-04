package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.springframework.stereotype.Component;

@Component
public class TicketAccessPolicy {

    public boolean isAdmin(AuthenticatedUser user) {
        return user.hasRole(RoleName.ADMIN);
    }

    /**
     * ADMIN y AGENT trabajan sobre la cola completa.
     */
    public boolean canViewAll(AuthenticatedUser user) {
        return isAdmin(user) || user.hasRole(RoleName.AGENT);
    }

    public boolean canView(AuthenticatedUser user, Ticket ticket) {
        return canViewAll(user) || isRequester(user, ticket);
    }

    public boolean isRequester(AuthenticatedUser user, Ticket ticket) {
        return ticket.getRequester().getId().equals(user.id());
    }

    public boolean isAssignedAgent(AuthenticatedUser user, Ticket ticket) {
        return user.hasRole(RoleName.AGENT)
                && ticket.getAgent() != null
                && ticket.getAgent().getId().equals(user.id());
    }

    /**
     * Control total: ADMIN, o el agente al que está asignado el ticket.
     */
    public boolean hasFullControl(AuthenticatedUser user, Ticket ticket) {
        return isAdmin(user) || isAssignedAgent(user, ticket);
    }

    /**
     * Permiso para pedir el cambio. Que la transición sea válida lo decide el dominio.
     */
    public boolean canChangeStatus(AuthenticatedUser user, Ticket ticket, TicketStatus target) {
        if (hasFullControl(user, ticket)) {
            return true;
        }
        if (!isRequester(user, ticket)) {
            return false;
        }
        TicketStatus current = ticket.getStatus();
        return (current == TicketStatus.OPEN && target == TicketStatus.CANCELLED)
                || (current == TicketStatus.RESOLVED
                && (target == TicketStatus.CLOSED || target == TicketStatus.IN_PROGRESS));
    }
}
