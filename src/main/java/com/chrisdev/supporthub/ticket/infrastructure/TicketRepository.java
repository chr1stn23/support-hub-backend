package com.chrisdev.supporthub.ticket.infrastructure;

import com.chrisdev.supporthub.ticket.domain.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    // Trae las asociaciones en la misma consulta del listado (evita N+1)
    @Override
    @EntityGraph(attributePaths = {"category", "requester", "agent", "asset"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "requester", "agent", "asset"})
    Optional<Ticket> findDetailById(UUID id);
}