package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.ticket.domain.TicketPriority;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TicketSearchCriteria(
        Set<TicketStatus> statuses,
        Set<TicketPriority> priorities,
        UUID categoryId,
        UUID assignedTo,
        Boolean unassigned,
        UUID assetId,
        Instant createdFrom,
        Instant createdTo,
        String search
) {
}
