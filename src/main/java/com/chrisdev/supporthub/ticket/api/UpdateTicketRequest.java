package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.ticket.domain.TicketPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateTicketRequest(
        @NotNull Long version,
        @Size(max = 200) String title,
        @Size(min = 10, max = 5000) String description,
        UUID categoryId,
        TicketPriority priority,
        UUID assetId
) {

    public boolean hasChanges() {
        return title != null || description != null || categoryId != null
                || priority != null || assetId != null;
    }
}