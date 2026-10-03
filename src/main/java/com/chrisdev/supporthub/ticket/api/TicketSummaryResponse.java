package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.ticket.domain.TicketPriority;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import com.chrisdev.supporthub.user.api.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record TicketSummaryResponse(
        UUID id,
        String ticketCode,
        String title,
        TicketStatus status,
        TicketPriority priority,
        String categoryName,
        UserSummary requester,
        UserSummary agent,
        Instant createdAt,
        Instant updatedAt
) {

    public static TicketSummaryResponse from(Ticket t) {
        return new TicketSummaryResponse(
                t.getId(), t.getTicketCode(), t.getTitle(), t.getStatus(), t.getPriority(),
                t.getCategory().getName(),
                UserSummary.from(t.getRequester()),
                UserSummary.from(t.getAgent()),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}