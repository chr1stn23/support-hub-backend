package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.springframework.stereotype.Component;

@Component
public class TicketAccessPolicy {

    /**
     * ADMIN y AGENT trabajan sobre la cola completa.
     */
    public boolean canViewAll(AuthenticatedUser user) {
        return user.hasRole(RoleName.ADMIN) || user.hasRole(RoleName.AGENT);
    }

    public boolean canView(AuthenticatedUser user, Ticket ticket) {
        return canViewAll(user) || ticket.getRequester().getId().equals(user.id());
    }
}
