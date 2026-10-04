package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.ticket.domain.TicketEventType;
import com.chrisdev.supporthub.ticket.domain.TicketHistory;
import com.chrisdev.supporthub.user.api.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record HistoryResponse(
        UUID id,
        TicketEventType eventType,
        UserSummary actor,
        String oldValue,
        String newValue,
        String note,
        Instant createdAt
) {

    public static HistoryResponse from(TicketHistory h) {
        return new HistoryResponse(h.getId(), h.getEventType(), UserSummary.from(h.getActor()),
                h.getOldValue(), h.getNewValue(), h.getNote(), h.getCreatedAt());
    }
}