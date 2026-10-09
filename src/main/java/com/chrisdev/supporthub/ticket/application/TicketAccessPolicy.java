package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.api.TicketActions;
import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Título y descripción. No mira si el ticket está bloqueado: eso da un error distinto (TICKET_LOCKED).
     */
    public boolean mayEditContent(AuthenticatedUser user, Ticket ticket) {
        return hasFullControl(user, ticket)
                || (isRequester(user, ticket) && ticket.getStatus() == TicketStatus.OPEN);
    }

    /**
     * Prioridad, categoría y equipo.
     */
    public boolean mayEditTriage(AuthenticatedUser user, Ticket ticket) {
        return hasFullControl(user, ticket);
    }

    /**
     * Un agente solo toma tickets sin agente; un admin puede además quitárselo a otro.
     */
    public boolean canAssignToSelf(AuthenticatedUser user, Ticket ticket) {
        if (!ticket.isAssignable() || !(isAdmin(user) || user.hasRole(RoleName.AGENT))) {
            return false;
        }
        if (ticket.getAgent() == null) {
            return true;
        }
        if (ticket.getAgent().getId().equals(user.id())) {
            return false;   // ya es suyo
        }
        return isAdmin(user);
    }

    public boolean canAssignToOthers(AuthenticatedUser user, Ticket ticket) {
        return ticket.isAssignable() && isAdmin(user);
    }

    /**
     * Lo que la interfaz necesita para decidir qué botones mostrar.
     */
    public TicketActions actionsFor(AuthenticatedUser user, Ticket ticket) {
        boolean open = !ticket.isLocked();

        List<TicketActions.StatusOption> options = new ArrayList<>();
        for (TicketStatus target : ticket.getStatus().allowedTransitions()) {
            if (ticket.canMoveTo(target) && canChangeStatus(user, ticket, target)) {
                boolean noteRequired =
                        ticket.getStatus() == TicketStatus.RESOLVED && target == TicketStatus.IN_PROGRESS;
                options.add(new TicketActions.StatusOption(target, noteRequired));
            }
        }

        return new TicketActions(
                open && mayEditContent(user, ticket),
                open && mayEditTriage(user, ticket),
                open,
                open,
                canAssignToSelf(user, ticket),
                canAssignToOthers(user, ticket),
                List.copyOf(options));
    }
}
