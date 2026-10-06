package com.chrisdev.supporthub.user.application;

import com.chrisdev.supporthub.common.api.PageResponse;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.user.api.*;
import com.chrisdev.supporthub.user.domain.RoleName;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final String ADMIN = "hasRole('ADMIN')";
    private static final String LOOKUP = "hasAnyRole('ADMIN', 'ASSET_MANAGER', 'AGENT')";

    private final UserCommandService commandService;
    private final UserQueryService queryService;

    public UserController(UserCommandService commandService, UserQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    // Selector

    @GetMapping("/lookup")
    @PreAuthorize(LOOKUP)
    public PageResponse<UserLookupResponse> lookup(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleName role,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return queryService.lookup(search, role, pageable);
    }

    // Administración

    @GetMapping
    @PreAuthorize(ADMIN)
    public PageResponse<UserAdminResponse> search(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return queryService.search(new UserSearchCriteria(active, role, search), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ADMIN)
    public UserAdminResponse get(@PathVariable UUID id) {
        return queryService.getById(id);
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<UserAdminResponse> create(@Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UserAdminResponse created = commandService.create(request, AuthenticatedUser.from(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(ADMIN)
    public UserAdminResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return commandService.update(id, request, AuthenticatedUser.from(jwt));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize(ADMIN)
    public UserAdminResponse changeRoles(@PathVariable UUID id, @Valid @RequestBody ChangeRolesRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return commandService.changeRoles(id, request, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(ADMIN)
    public UserAdminResponse deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return commandService.deactivate(id, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(ADMIN)
    public UserAdminResponse activate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return commandService.activate(id, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/password")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        commandService.resetPassword(id, request, AuthenticatedUser.from(jwt));
    }

    // Cualquier usuario autenticado
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        commandService.changeOwnPassword(request, AuthenticatedUser.from(jwt));
    }
}

