package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.infrastructure.AssetAssignmentRepository;
import com.chrisdev.supporthub.asset.infrastructure.AssetRepository;
import com.chrisdev.supporthub.audit.application.AuditMetadata;
import com.chrisdev.supporthub.audit.application.AuditService;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.notification.application.NotificationService;
import com.chrisdev.supporthub.notification.domain.NotificationType;
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
    private final AssetAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final TicketLookup lookup;
    private final TicketAccessPolicy policy;
    private final AuditService audit;
    private final NotificationService notifications;

    public TicketCommandService(TicketRepository ticketRepository,
            TicketHistoryRepository historyRepository,
            TicketCommentRepository commentRepository,
            CategoryRepository categoryRepository,
            AssetRepository assetRepository,
            AssetAssignmentRepository assignmentRepository,
            UserRepository userRepository,
            TicketLookup lookup,
            TicketAccessPolicy policy,
            AuditService audit,
            NotificationService notifications) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.commentRepository = commentRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.lookup = lookup;
        this.policy = policy;
        this.audit = audit;
        this.notifications = notifications;
    }

    /**
     * Solicitante y agente asignado (si hay).
     */
    private static List<UUID> stakeholders(Ticket ticket) {
        List<UUID> ids = new ArrayList<>();
        ids.add(ticket.getRequester().getId());
        if (ticket.getAgent() != null) {
            ids.add(ticket.getAgent().getId());
        }
        return ids;
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
        Asset asset = request.assetId() == null ? null : findLinkableAsset(request.assetId(), actor);

        Ticket ticket = Ticket.open(request.title(), request.description(),
                category, request.priority(), requester, asset);

        // saveAndFlush fuerza el INSERT ahora, para que Hibernate lea el ticket_code generado por la BD
        ticketRepository.saveAndFlush(ticket);

        historyRepository.save(TicketHistory.of(ticket, requester, TicketEventType.CREATED,
                null, TicketStatus.OPEN.name(), null));

        audit.record(actor.id(), AuditAction.TICKET_CREATED, AuditEntityType.TICKET, ticket.getId(),
                AuditMetadata.of("ticketCode", ticket.getTicketCode(),
                        "priority", ticket.getPriority(), "category", category.getName()));
        notifications.notifyUsers(NotificationType.TICKET_CREATED,
                "Nuevo ticket %s: %s".formatted(ticket.getTicketCode(), ticket.getTitle()),
                ticket.getId(), actor.id(), userRepository.findActiveIdsByRole(RoleName.AGENT));

        return respond(ticket, actor);
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
            return respond(ticket, actor);   // ya estaba asignado a esa persona: no hay nada que hacer
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
            // Sin nota: la interfaz ya sabe explicar "OPEN → IN_PROGRESS" a partir de los dos eventos
            historyRepository.save(TicketHistory.of(ticket, actorRef, TicketEventType.STATUS_CHANGED,
                    TicketStatus.OPEN.name(), TicketStatus.IN_PROGRESS.name(), null));
        }

        audit.record(actor.id(), AuditAction.TICKET_ASSIGNED, AuditEntityType.TICKET, ticket.getId(),
                AuditMetadata.of("ticketCode", ticket.getTicketCode(),
                        "from", previous == null ? null : previous.getFullName(),
                        "to", assignee.getFullName(),
                        "status", started ? TicketStatus.IN_PROGRESS : null));
        notifications.notifyUsers(NotificationType.TICKET_ASSIGNED,
                "El ticket %s fue asignado a %s".formatted(ticket.getTicketCode(), assignee.getFullName()),
                ticket.getId(), actor.id(), List.of(ticket.getRequester().getId(), assignee.getId()));

        return respond(ticket, actor);
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

        audit.record(actor.id(), AuditAction.TICKET_STATUS_CHANGED, AuditEntityType.TICKET, ticket.getId(),
                AuditMetadata.of("ticketCode", ticket.getTicketCode(), "from", from, "to", to));
        notifications.notifyUsers(NotificationType.TICKET_STATUS_CHANGED,
                "El ticket %s cambió de «%s» a «%s»".formatted(ticket.getTicketCode(), from.label(), to.label()),
                ticket.getId(), actor.id(), stakeholders(ticket));

        return respond(ticket, actor);
    }

    // Editar
    @Transactional
    public TicketResponse update(UUID ticketId, UpdateTicketRequest request, AuthenticatedUser actor) {
        Ticket ticket = lookup.requireVisible(ticketId, actor);

        if (!request.hasChanges()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No changes were provided.");
        }

        // Las mismas dos preguntas que responde `actions.editContent` y `actions.editTriage`
        boolean touchesTriage = request.categoryId() != null || request.priority() != null || request.assetId() != null;
        if (!policy.mayEditContent(actor, ticket)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not allowed to edit this ticket.");
        }
        if (touchesTriage && !policy.mayEditTriage(actor, ticket)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Only the assigned agent or an admin can change category, priority or asset.");
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
            Asset asset = findAsset(request.assetId());   // quien llega aquí tiene control total: cualquier equipo
            events.add(detailsChanged(ticket, actorRef, "asset",
                    ticket.getAsset() == null ? null : ticket.getAsset().getAssetTag(), asset.getAssetTag()));
            ticket.linkAsset(asset);
        }

        if (events.isEmpty()) {
            return respond(ticket, actor);   // los valores enviados ya eran los actuales
        }

        ticketRepository.saveAndFlush(ticket);
        historyRepository.saveAll(events);

        audit.record(actor.id(), AuditAction.TICKET_UPDATED, AuditEntityType.TICKET, ticket.getId(),
                AuditMetadata.of("ticketCode", ticket.getTicketCode(),
                        "fields", events.stream().map(TicketHistory::getNote).toList()));
        return respond(ticket, actor);
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

        audit.record(actor.id(), AuditAction.TICKET_COMMENT_ADDED, AuditEntityType.TICKET, ticket.getId(),
                AuditMetadata.of("ticketCode", ticket.getTicketCode(), "commentId", comment.getId()));
        notifications.notifyUsers(NotificationType.TICKET_COMMENT_ADDED,
                "Nuevo comentario en el ticket %s".formatted(ticket.getTicketCode()),
                ticket.getId(), actor.id(), stakeholders(ticket));

        return CommentResponse.from(comment);
    }

    // Helpers
    private TicketResponse respond(Ticket ticket, AuthenticatedUser actor) {
        return TicketResponse.from(ticket, policy.actionsFor(actor, ticket));
    }

    private User requireActiveUser(AuthenticatedUser actor) {
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

    /**
     * El personal de TI puede vincular cualquier equipo; una persona común, solo los que tiene asignados.
     * El mensaje es el mismo si el equipo no existe o si no es suyo, para no revelar el inventario.
     */
    private Asset findLinkableAsset(UUID assetId, AuthenticatedUser actor) {
        boolean staff = actor.hasRole(RoleName.ADMIN) || actor.hasRole(RoleName.AGENT)
                || actor.hasRole(RoleName.ASSET_MANAGER);
        if (!staff && !assignmentRepository.existsByAssetIdAndUserIdAndReturnedAtIsNull(assetId, actor.id())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The selected asset does not exist or is not assigned to you.");
        }
        return findAsset(assetId);
    }
}