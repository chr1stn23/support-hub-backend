package com.chrisdev.supporthub.ticket.application;


import com.chrisdev.supporthub.common.api.PageResponse;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.exception.ResourceNotFoundException;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.api.TicketResponse;
import com.chrisdev.supporthub.ticket.api.TicketSummaryResponse;
import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.ticket.infrastructure.TicketRepository;
import com.chrisdev.supporthub.ticket.infrastructure.TicketSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TicketQueryService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "updatedAt", "title", "ticketCode");
    private static final int MAX_SEARCH_LENGTH = 100;

    private final TicketRepository ticketRepository;
    private final TicketAccessPolicy accessPolicy;

    public TicketQueryService(TicketRepository ticketRepository, TicketAccessPolicy accessPolicy) {
        this.ticketRepository = ticketRepository;
        this.accessPolicy = accessPolicy;
    }

    public PageResponse<TicketSummaryResponse> search(
            TicketSearchCriteria criteria,
            Pageable pageable,
            AuthenticatedUser user
    ) {
        validateSort(pageable);

        Specification<Ticket> spec = TicketSpecifications.all();

        if (!accessPolicy.canViewAll(user)) {
            spec = spec.and(TicketSpecifications.requesterIs(user.id()));
        }
        if (criteria.status() != null) {
            spec = spec.and(TicketSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.priority() != null) {
            spec = spec.and(TicketSpecifications.hasPriority(criteria.priority()));
        }
        if (criteria.categoryId() != null) {
            spec = spec.and(TicketSpecifications.inCategory(criteria.categoryId()));
        }
        if (criteria.assignedTo() != null) {
            spec = spec.and(TicketSpecifications.assignedTo(criteria.assignedTo()));
        }
        if (Boolean.TRUE.equals(criteria.unassigned())) {
            spec = spec.and(TicketSpecifications.unassigned());
        }
        if (criteria.search() != null && !criteria.search().isBlank()) {
            String text = criteria.search().trim();
            if (text.length() > MAX_SEARCH_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "The search text cannot exceed %d characters.".formatted(MAX_SEARCH_LENGTH));
            }
            spec = spec.and(TicketSpecifications.matches(text));
        }

        Page<Ticket> page = ticketRepository.findAll(spec, pageable);
        return PageResponse.from(page, TicketSummaryResponse::from);
    }

    public TicketResponse getById(UUID id, AuthenticatedUser user) {
        Ticket ticket = ticketRepository.findDetailById(id)
                .filter(t -> accessPolicy.canView(user, t))
                // 404 tanto si no existe como si no tiene permiso: no revelar que existe
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        return TicketResponse.from(ticket);
    }

    private void validateSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Cannot sort by '%s'. Allowed fields: %s."
                                .formatted(order.getProperty(), SORTABLE_FIELDS));
            }
        }
    }
}
