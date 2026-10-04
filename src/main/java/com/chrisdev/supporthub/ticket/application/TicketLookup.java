package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.common.exception.ResourceNotFoundException;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.ticket.infrastructure.TicketRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TicketLookup {

    private final TicketRepository ticketRepository;
    private final TicketAccessPolicy accessPolicy;

    public TicketLookup(TicketRepository ticketRepository, TicketAccessPolicy accessPolicy) {
        this.ticketRepository = ticketRepository;
        this.accessPolicy = accessPolicy;
    }

    /**
     * 404 tanto si no existe como si el usuario no puede verlo.
     */
    public Ticket requireVisible(UUID id, AuthenticatedUser user) {
        return ticketRepository.findDetailById(id)
                .filter(t -> accessPolicy.canView(user, t))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
    }
}
