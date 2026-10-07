package com.chrisdev.supporthub.ticket.infrastructure;

import com.chrisdev.supporthub.ticket.domain.TicketAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    @EntityGraph(attributePaths = "uploadedBy")
    List<TicketAttachment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    // Buscar por id Y por ticket: impide pedir el adjunto de un ticket usando la URL de otro (IDOR)
    @EntityGraph(attributePaths = "uploadedBy")
    Optional<TicketAttachment> findByIdAndTicketId(UUID id, UUID ticketId);

    long countByTicketId(UUID ticketId);
}
