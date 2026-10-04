package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.common.api.PageResponse;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.application.TicketCommandService;
import com.chrisdev.supporthub.ticket.application.TicketQueryService;
import com.chrisdev.supporthub.ticket.application.TicketSearchCriteria;
import com.chrisdev.supporthub.ticket.domain.TicketPriority;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketCommandService commandService;
    private final TicketQueryService queryService;

    public TicketController(TicketCommandService commandService, TicketQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        TicketResponse created = commandService.create(request, AuthenticatedUser.from(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<TicketSummaryResponse> search(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        var criteria = new TicketSearchCriteria(status, priority, categoryId, assignedTo, unassigned, search);
        return queryService.search(criteria, pageable, AuthenticatedUser.from(jwt));
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queryService.getById(id, AuthenticatedUser.from(jwt));
    }

    @PatchMapping("/{id}")
    public TicketResponse update(@PathVariable UUID id,
            @Valid @RequestBody UpdateTicketRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return commandService.update(id, request, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public TicketResponse assign(@PathVariable UUID id,
            @RequestBody(required = false) AssignTicketRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return commandService.assign(id, request, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/status")
    public TicketResponse changeStatus(@PathVariable UUID id,
            @Valid @RequestBody ChangeTicketStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return commandService.changeStatus(id, request, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable UUID id,
            @Valid @RequestBody AddCommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commandService.addComment(id, request, AuthenticatedUser.from(jwt)));
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queryService.getComments(id, AuthenticatedUser.from(jwt));
    }

    @GetMapping("/{id}/history")
    public List<HistoryResponse> history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queryService.getHistory(id, AuthenticatedUser.from(jwt));
    }
}
