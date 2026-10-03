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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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
}
