package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeTicketStatusRequest(
        @NotNull TicketStatus status,
        @Size(max = 500) String note
) {
}