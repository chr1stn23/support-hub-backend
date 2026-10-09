package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.asset.api.AssetSummary;
import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.ticket.domain.TicketPriority;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import com.chrisdev.supporthub.user.api.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String ticketCode,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        CategoryResponse category,
        UserSummary requester,
        UserSummary agent,
        AssetSummary asset,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        Long version,
        TicketActions actions
) {

    public static TicketResponse from(Ticket t, TicketActions actions) {
        return new TicketResponse(
                t.getId(), t.getTicketCode(), t.getTitle(), t.getDescription(),
                t.getStatus(), t.getPriority(),
                CategoryResponse.from(t.getCategory()),
                UserSummary.from(t.getRequester()),
                UserSummary.from(t.getAgent()),
                AssetSummary.from(t.getAsset()),
                t.getCreatedAt(), t.getUpdatedAt(), t.getResolvedAt(), t.getClosedAt(),
                t.getVersion(), actions);
    }
}