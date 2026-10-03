package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.ticket.domain.TicketPriority;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;

import java.util.UUID;

public record TicketSearchCriteria(
        TicketStatus status,
        TicketPriority priority,
        UUID categoryId,
        UUID assignedTo,
        Boolean unassigned,
        String search
) {
}
