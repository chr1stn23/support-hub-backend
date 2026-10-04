package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.ticket.domain.TicketComment;
import com.chrisdev.supporthub.user.api.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, UserSummary author, String body, Instant createdAt) {

    public static CommentResponse from(TicketComment c) {
        return new CommentResponse(c.getId(), UserSummary.from(c.getAuthor()), c.getBody(), c.getCreatedAt());
    }
}