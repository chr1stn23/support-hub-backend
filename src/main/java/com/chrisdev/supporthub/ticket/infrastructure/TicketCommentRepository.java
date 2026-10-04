package com.chrisdev.supporthub.ticket.infrastructure;

import com.chrisdev.supporthub.ticket.domain.TicketComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    @EntityGraph(attributePaths = "author")
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
