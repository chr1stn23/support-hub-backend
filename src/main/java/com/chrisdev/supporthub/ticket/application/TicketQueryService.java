package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.common.api.PageResponse;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.api.CommentResponse;
import com.chrisdev.supporthub.ticket.api.HistoryResponse;
import com.chrisdev.supporthub.ticket.api.TicketResponse;
import com.chrisdev.supporthub.ticket.api.TicketSummaryResponse;
import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.ticket.infrastructure.TicketCommentRepository;
import com.chrisdev.supporthub.ticket.infrastructure.TicketHistoryRepository;
import com.chrisdev.supporthub.ticket.infrastructure.TicketRepository;
import com.chrisdev.supporthub.ticket.infrastructure.TicketSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class TicketQueryService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "updatedAt", "title", "ticketCode", "priority");
    // "priority" ordena por la columna generada priority_rank (CRITICAL > HIGH > MEDIUM > LOW), no alfabéticamente
    private static final Map<String, String> SORT_ALIASES = Map.of("priority", "priorityRank");
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final int RELATED_TICKETS_LIMIT = 25;

    private final TicketRepository ticketRepository;
    private final TicketAccessPolicy accessPolicy;
    private final TicketLookup lookup;
    private final TicketCommentRepository commentRepository;
    private final TicketHistoryRepository historyRepository;

    public TicketQueryService(TicketRepository ticketRepository, TicketAccessPolicy accessPolicy,
            TicketLookup lookup, TicketCommentRepository commentRepository,
            TicketHistoryRepository historyRepository) {
        this.ticketRepository = ticketRepository;
        this.accessPolicy = accessPolicy;
        this.lookup = lookup;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
    }

    public PageResponse<TicketSummaryResponse> search(TicketSearchCriteria criteria, Pageable pageable,
            AuthenticatedUser user) {
        Pageable entityPageable = toEntityPageable(pageable);

        if (criteria.createdFrom() != null && criteria.createdTo() != null
                && criteria.createdFrom().isAfter(criteria.createdTo())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "'createdFrom' cannot be after 'createdTo'.");
        }

        Specification<Ticket> spec = TicketSpecifications.all();

        // Regla de visibilidad: se aplica SIEMPRE, antes que cualquier filtro del cliente
        if (!accessPolicy.canViewAll(user)) {
            spec = spec.and(TicketSpecifications.requesterIs(user.id()));
        }
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            spec = spec.and(TicketSpecifications.statusIn(criteria.statuses()));
        }
        if (criteria.priorities() != null && !criteria.priorities().isEmpty()) {
            spec = spec.and(TicketSpecifications.priorityIn(criteria.priorities()));
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
        if (criteria.assetId() != null) {
            spec = spec.and(TicketSpecifications.forAsset(criteria.assetId()));
        }
        if (criteria.createdFrom() != null) {
            spec = spec.and(TicketSpecifications.createdFrom(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            spec = spec.and(TicketSpecifications.createdBefore(criteria.createdTo()));
        }
        if (criteria.search() != null && !criteria.search().isBlank()) {
            String text = criteria.search().trim();
            if (text.length() > MAX_SEARCH_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "The search text cannot exceed %d characters.".formatted(MAX_SEARCH_LENGTH));
            }
            spec = spec.and(TicketSpecifications.matches(text));
        }

        Page<Ticket> page = ticketRepository.findAll(spec, entityPageable);
        return PageResponse.from(page, t -> TicketSummaryResponse.from(t, accessPolicy.canAssignToSelf(user, t)));
    }

    public TicketResponse getById(UUID id, AuthenticatedUser user) {
        Ticket ticket = lookup.requireVisible(id, user);
        return TicketResponse.from(ticket, accessPolicy.actionsFor(user, ticket));
    }

    /**
     * Tickets de un equipo. Para los roles que gestionan inventario es un resumen: NO aplica la regla de
     * "solo los propios", porque el panel de un equipo sirve justamente para ver su historial de problemas.
     */
    public List<TicketSummaryResponse> recentForAsset(UUID assetId, AuthenticatedUser user) {
        Pageable newestFirst = PageRequest.of(0, RELATED_TICKETS_LIMIT,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return ticketRepository.findAll(TicketSpecifications.forAsset(assetId), newestFirst).stream()
                .map(t -> TicketSummaryResponse.from(t, accessPolicy.canAssignToSelf(user, t)))
                .toList();
    }

    public List<CommentResponse> getComments(UUID ticketId, AuthenticatedUser user) {
        lookup.requireVisible(ticketId, user);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    public List<HistoryResponse> getHistory(UUID ticketId, AuthenticatedUser user) {
        lookup.requireVisible(ticketId, user);
        return historyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(HistoryResponse::from)
                .toList();
    }

    /**
     * Valida los campos de orden, traduce los alias y añade desempates para que la paginación sea estable.
     */
    private Pageable toEntityPageable(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Cannot sort by '%s'. Allowed fields: %s.".formatted(order.getProperty(), SORTABLE_FIELDS));
            }
            String property = SORT_ALIASES.getOrDefault(order.getProperty(), order.getProperty());
            orders.add(new Sort.Order(order.getDirection(), property));
        }
        if (orders.stream().noneMatch(o -> o.getProperty().equals("createdAt"))) {
            orders.add(Sort.Order.desc("createdAt"));
        }
        orders.add(Sort.Order.asc("id"));   // sin desempate único, filas iguales pueden repetirse o saltarse entre
        // páginas
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }
}