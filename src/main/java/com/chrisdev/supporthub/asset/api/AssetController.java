package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.application.AssetCommandService;
import com.chrisdev.supporthub.asset.application.AssetQueryService;
import com.chrisdev.supporthub.asset.application.AssetSearchCriteria;
import com.chrisdev.supporthub.asset.domain.AssetStatus;
import com.chrisdev.supporthub.common.api.PageResponse;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/assets")
public class AssetController {

    private static final String READERS = "hasAnyRole('ADMIN', 'ASSET_MANAGER', 'AGENT')";
    private static final String MANAGERS = "hasAnyRole('ADMIN', 'ASSET_MANAGER')";

    private final AssetCommandService commandService;
    private final AssetQueryService queryService;

    public AssetController(AssetCommandService commandService, AssetQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    // Lectura

    @GetMapping
    @PreAuthorize(READERS)
    public PageResponse<AssetSummaryResponse> search(
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) UUID assetTypeId,
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "assetTag", direction = Sort.Direction.ASC) Pageable pageable) {
        return queryService.search(new AssetSearchCriteria(status, assetTypeId, search), pageable);
    }

    /**
     * Los activos que tiene asignados el usuario autenticado (cualquier rol).
     */
    @GetMapping("/mine")
    public List<AssetSummaryResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return queryService.myAssets(AuthenticatedUser.from(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READERS)
    public AssetResponse get(@PathVariable UUID id) {
        return queryService.getById(id);
    }

    @GetMapping("/{id}/assignments")
    @PreAuthorize(READERS)
    public List<AssignmentResponse> assignments(@PathVariable UUID id) {
        return queryService.getAssignments(id);
    }

    // Escritura

    @PostMapping
    @PreAuthorize(MANAGERS)
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody CreateAssetRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AssetResponse created = commandService.register(request, AuthenticatedUser.from(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(MANAGERS)
    public AssetResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAssetRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return commandService.update(id, request, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(MANAGERS)
    public AssetResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignAssetRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return commandService.assign(id, request, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize(MANAGERS)
    public AssetResponse transfer(@PathVariable UUID id, @Valid @RequestBody AssignAssetRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return commandService.transfer(id, request, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(MANAGERS)
    public AssetResponse returnAsset(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return commandService.returnAsset(id, AuthenticatedUser.from(jwt));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize(MANAGERS)
    public AssetResponse changeStatus(@PathVariable UUID id,
            @Valid @RequestBody ChangeAssetStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return commandService.changeStatus(id, request, AuthenticatedUser.from(jwt));
    }
}
