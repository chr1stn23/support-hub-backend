package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.ticket.domain.TicketAttachment;
import com.chrisdev.supporthub.user.api.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        UserSummary uploadedBy,
        Instant createdAt
) {

    public static AttachmentResponse from(TicketAttachment a) {
        return new AttachmentResponse(a.getId(), a.getOriginalFilename(), a.getContentType(),
                a.getSizeBytes(), UserSummary.from(a.getUploadedBy()), a.getCreatedAt());
    }
}
