package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.infrastructure.AssetRepository;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.api.*;
import com.chrisdev.supporthub.ticket.domain.*;
import com.chrisdev.supporthub.ticket.infrastructure.CategoryRepository;
import com.chrisdev.supporthub.ticket.infrastructure.TicketCommentRepository;
import com.chrisdev.supporthub.ticket.infrastructure.TicketHistoryRepository;
import com.chrisdev.supporthub.ticket.infrastructure.TicketRepository;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TicketCommandService {

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final TicketCommentRepository commentRepository;
    private final CategoryRepository categoryRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final TicketLookup lookup;
    private final TicketAccessPolicy policy;

    public TicketCommandService(TicketRepository ticketRepository, TicketHistoryRepository historyRepository,
            TicketCommentRepository commentRepository, CategoryRepository categoryRepository,
            AssetRepository assetRepository, UserRepository userRepository, TicketLookup lookup,
            TicketAccessPolicy policy) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.commentRepository = commentRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.lookup = lookup;
        this.policy = policy;
    }

    private static TicketHistory detailsChanged(Ticket ticket, User actor, String field,
            String oldValue, String newValue) {
        return TicketHistory.of(ticket, actor, TicketEventType.DETAILS_UPDATED, oldValue, newValue, field);
    }

    private static String requireText(String field, String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The field '%s' cannot be blank.".formatted(field));
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // Crear
    @Transactional
    public TicketResponse create(CreateTicketRequest request, AuthenticatedUser actor) {
        User requester = requireActiveUser(actor);
        Category category = findActiveCategory(request.categoryId());
        Asset asset = request.assetId() == null ? null : findAsset(request.assetId());

        Ticket ticket = Ticket.open(request.title(), request.description(),
                category, request.priority(), requester, asset);

        ticketRepository.saveAndFlush(ticket);

        historyRepository.save(TicketHistory.of(ticket, requester, TicketEventType.CREATED,
                null, TicketStatus.OPEN.name(), null));

        return TicketResponse.from(ticket);
    }

    // Asignar
    @Transactional
    public TicketResponse assign(UUID ticketId, AssignTicketRequest request, AuthenticatedUser actor) {
        Ticket ticket = lookup.requireVisible(ticketId, actor);

        // Sin agentId, el actor se asigna a sí mismo ("tomar el ticket")
        UUID targetId = (request == null || request.agentId() == null) ? actor.id() : request.agentId();

        if (!policy.isAdmin(actor) && !targetId.equals(actor.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agents can only assign tickets to themselves.");
        }

        User assignee = userRepository.findWithRolesById(targetId)
                .filter(User::isActive)
                .filter(u -> u.hasRole(RoleName.AGENT) || u.hasRole(RoleName.ADMIN))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ASSIGNEE,
                        "The assignee must be an active agent or admin."));

        if (!ticket.isAssignable()) {
            throw new BusinessException(ErrorCode.TICKET_NOT_ASSIGNABLE,
                    "A ticket in status %s cannot be assigned.".formatted(ticket.getStatus()));
        }

        User previous = ticket.getAgent();
        if (previous != null && previous.getId().equals(assignee.getId())) {
            return TicketResponse.from(ticket);   // ya estaba asignado a esa persona: no hay nada que hacer
        }

        if (previous != null && !policy.isAdmin(actor)) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_ASSIGNED,
                    "The ticket is already assigned to %s. Only an admin can reassign it."
                            .formatted(previous.getFullName()));
        }

        User actorRef = userRepository.getReferenceById(actor.id());
        boolean started = ticket.assignTo(assignee);
        ticketRepository.saveAndFlush(ticket);

        historyRepository.save(TicketHistory.of(ticket, actorRef, TicketEventType.ASSIGNED,
                previous == null ? null : previous.getFullName(), assignee.getFullName(), null));
        if (started) {
            historyRepository.save(TicketHistory.of(ticket, actorRef, TicketEventType.STATUS_CHANGED,
                    TicketStatus.OPEN.name(), TicketStatus.IN_PROGRESS.name(),
                    "Automatically moved to IN_PROGRESS on assignment."));
        }

        return TicketResponse.from(ticket);
    }

    // Cambiar estado
    @Transactional
    public TicketResponse changeStatus(UUID ticketId, ChangeTicketStatusRequest request, AuthenticatedUser actor) {
        Ticket ticket = lookup.requireVisible(ticketId, actor);
        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.status();

        if (!policy.canChangeStatus(actor, ticket, to)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "You are not allowed to move this ticket from %s to %s.".formatted(from, to));
        }

        String note = blankToNull(request.note());
        if (from == TicketStatus.RESOLVED && to == TicketStatus.IN_PROGRESS && note == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "A note explaining the reason is required to reopen a ticket.");
        }

        ticket.changeStatus(to);   // el dominio lanza 409 si la transición no es válida
        ticketRepository.saveAndFlush(ticket);

        historyRepository.save(TicketHistory.of(ticket, userRepository.getReferenceById(actor.id()),
                TicketEventType.STATUS_CHANGED, from.name(), to.name(), note));
        return TicketResponse.from(ticket);
    }

    // Editar
    @Transactional
    public TicketResponse update(UUID ticketId, UpdateTicketRequest request, AuthenticatedUser actor) {
        Ticket ticket = lookup.requireVisible(ticketId, actor);

        if (!request.hasChanges()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No changes were provided.");
        }

        if (!policy.hasFullControl(actor, ticket)) {
            if (!policy.isRequester(actor, ticket)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "You are not allowed to edit this ticket.");
            }
            if (ticket.getStatus() != TicketStatus.OPEN) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "You can only edit your ticket while it is OPEN.");
            }
            if (request.categoryId() != null || request.priority() != null || request.assetId() != null) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "Only the assigned agent or an admin can change category, priority or asset.");
            }
        }

        if (ticket.isLocked()) {
            throw new BusinessException(ErrorCode.TICKET_LOCKED,
                    "A ticket in status %s can no longer be edited.".formatted(ticket.getStatus()));
        }

        // Control explícito: ¿el cliente editó sobre la versión que sigue vigente?
        if (!Objects.equals(ticket.getVersion(), request.version())) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "The ticket was modified by someone else. Reload it and try again.");
        }

        User actorRef = userRepository.getReferenceById(actor.id());
        List<TicketHistory> events = new ArrayList<>();

        if (request.title() != null) {
            String title = requireText("title", request.title());
            if (!title.equals(ticket.getTitle())) {
                events.add(detailsChanged(ticket, actorRef, "title", ticket.getTitle(), title));
                ticket.changeTitle(title);
            }
        }
        if (request.description() != null) {
            String description = requireText("description", request.description());
            if (!description.equals(ticket.getDescription())) {
                // La descripción puede ser larga: registramos el cambio, no los textos
                events.add(detailsChanged(ticket, actorRef, "description", null, null));
                ticket.changeDescription(description);
            }
        }
        if (request.priority() != null && request.priority() != ticket.getPriority()) {
            events.add(detailsChanged(ticket, actorRef, "priority",
                    ticket.getPriority().name(), request.priority().name()));
            ticket.changePriority(request.priority());
        }
        if (request.categoryId() != null && !request.categoryId().equals(ticket.getCategory().getId())) {
            Category category = findActiveCategory(request.categoryId());
            events.add(detailsChanged(ticket, actorRef, "category",
                    ticket.getCategory().getName(), category.getName()));
            ticket.changeCategory(category);
        }
        if (request.assetId() != null
                && (ticket.getAsset() == null || !request.assetId().equals(ticket.getAsset().getId()))) {
            Asset asset = findAsset(request.assetId());
            events.add(detailsChanged(ticket, actorRef, "asset",
                    ticket.getAsset() == null ? null : ticket.getAsset().getAssetTag(), asset.getAssetTag()));
            ticket.linkAsset(asset);
        }
        if (events.isEmpty()) {
            return TicketResponse.from(ticket);   // los valores enviados ya eran los actuales
        }

        ticketRepository.saveAndFlush(ticket);
        historyRepository.saveAll(events);
        return TicketResponse.from(ticket);
    }

    // Comentarios
    @Transactional
    public CommentResponse addComment(UUID ticketId, AddCommentRequest request, AuthenticatedUser actor) {
        Ticket ticket = lookup.requireVisible(ticketId, actor);

        if (ticket.isLocked()) {
            throw new BusinessException(ErrorCode.TICKET_LOCKED,
                    "A ticket in status %s no longer accepts comments.".formatted(ticket.getStatus()));
        }

        User author = requireActiveUser(actor);
        TicketComment comment = commentRepository.saveAndFlush(
                TicketComment.of(ticket, author, request.body().trim()));
        return CommentResponse.from(comment);
    }

    // Helpers
    private User requireActiveUser(AuthenticatedUser actor) {
        // El JWT sigue siendo válido aunque desactiven al usuario, así que lo comprobamos al escribir
        return userRepository.findById(actor.id())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "The authenticated user no longer exists or is inactive."));
    }

    private Category findActiveCategory(UUID id) {
        return categoryRepository.findById(id)
                .filter(Category::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "The selected category does not exist or is inactive."));
    }

    private Asset findAsset(UUID id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "The selected asset does not exist."));
    }
}
