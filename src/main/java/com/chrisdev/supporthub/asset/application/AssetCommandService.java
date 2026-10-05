package com.chrisdev.supporthub.asset.application;

import com.chrisdev.supporthub.asset.api.*;
import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.domain.AssetAssignment;
import com.chrisdev.supporthub.asset.domain.AssetStatus;
import com.chrisdev.supporthub.asset.domain.AssetType;
import com.chrisdev.supporthub.asset.infrastructure.AssetAssignmentRepository;
import com.chrisdev.supporthub.asset.infrastructure.AssetRepository;
import com.chrisdev.supporthub.asset.infrastructure.AssetTypeRepository;
import com.chrisdev.supporthub.audit.application.AuditMetadata;
import com.chrisdev.supporthub.audit.application.AuditService;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.exception.ResourceNotFoundException;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.common.util.Texts;
import com.chrisdev.supporthub.notification.application.NotificationService;
import com.chrisdev.supporthub.notification.domain.NotificationType;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AssetCommandService {

    private static final Logger log = LoggerFactory.getLogger(AssetCommandService.class);
    private static final String ACTIVE_ASSIGNMENT_INDEX = "uq_asset_assignments_active";
    private static final String SERIAL_INDEX = "uq_assets_serial";

    private final AuditService audit;
    private final NotificationService notifications;
    private final AssetRepository assetRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final AssetAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    public AssetCommandService(AuditService audit, NotificationService notifications, AssetRepository assetRepository,
            AssetTypeRepository assetTypeRepository,
            AssetAssignmentRepository assignmentRepository,
            UserRepository userRepository) {
        this.audit = audit;
        this.notifications = notifications;
        this.assetRepository = assetRepository;
        this.assetTypeRepository = assetTypeRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
    }

    private static String normalizeSerial(String value) {
        String serial = Texts.blankToNull(value);
        return serial == null ? null : serial.toUpperCase(Locale.ROOT);
    }

    private static BusinessException duplicateSerial() {
        return new BusinessException(ErrorCode.DUPLICATE_SERIAL_NUMBER,
                "Another asset already uses that serial number.");
    }

    private static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }

    // Alta
    @Transactional
    public AssetResponse register(CreateAssetRequest request, AuthenticatedUser actor) {
        AssetType type = findActiveType(request.assetTypeId());
        String serial = normalizeSerial(request.serialNumber());
        ensureSerialIsFree(serial, null);

        Asset asset = Asset.register(type,
                Texts.requireText("brand", request.brand()),
                Texts.requireText("model", request.model()),
                serial,
                Texts.blankToNull(request.location()));

        saveCatchingDuplicateSerial(asset);

        audit.record(actor.id(), AuditAction.ASSET_CREATED, AuditEntityType.ASSET, asset.getId(),
                AuditMetadata.of("assetTag", asset.getAssetTag(), "type", type.getName()));

        return AssetResponse.from(asset, null);
    }

    // Edición
    @Transactional
    public AssetResponse update(UUID assetId, UpdateAssetRequest request, AuthenticatedUser actor) {
        Asset asset = requireAsset(assetId);

        if (!request.hasChanges()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No changes were provided.");
        }
        if (asset.isRetired()) {
            throw new BusinessException(ErrorCode.ASSET_RETIRED, "A retired asset can no longer be edited.");
        }
        if (!Objects.equals(asset.getVersion(), request.version())) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "The asset was modified by someone else. Reload it and try again.");
        }

        List<String> changed = new ArrayList<>();

        if (request.assetTypeId() != null
                && !request.assetTypeId().equals(asset.getAssetType().getId())) {
            asset.changeType(findActiveType(request.assetTypeId()));
            changed.add("assetType");
        }
        if (request.brand() != null) {
            asset.changeBrand(Texts.requireText("brand", request.brand()));
            changed.add("brand");
        }
        if (request.model() != null) {
            asset.changeModel(Texts.requireText("model", request.model()));
            changed.add("model");
        }
        if (request.serialNumber() != null) {
            String serial = normalizeSerial(request.serialNumber());
            if (!Objects.equals(serial, asset.getSerialNumber())) {
                ensureSerialIsFree(serial, asset.getId());
                asset.changeSerialNumber(serial);
                changed.add("serialNumber");
            }
        }
        if (request.location() != null) {
            asset.changeLocation(Texts.blankToNull(request.location()));
            changed.add("location");
        }

        saveCatchingDuplicateSerial(asset);

        if (!changed.isEmpty()) {
            audit.record(actor.id(), AuditAction.ASSET_UPDATED, AuditEntityType.ASSET, asset.getId(),
                    AuditMetadata.of("assetTag", asset.getAssetTag(), "fields", changed));
        }

        return AssetResponse.from(asset, currentAssignment(assetId).orElse(null));
    }

    // Asignar
    @Transactional
    public AssetResponse assign(UUID assetId, AssignAssetRequest request, AuthenticatedUser actor) {
        Asset asset = requireAsset(assetId);
        User assignee = requireActiveAssignee(request.userId());
        ensureAssignable(asset);

        AssetAssignment assignment = AssetAssignment.open(asset, assignee,
                userRepository.getReferenceById(actor.id()), Texts.blankToNull(request.notes()));
        asset.markAssigned();

        try {
            // Este flush manda el INSERT de la asignación y después el UPDATE del activo
            // (Hibernate ejecuta los inserts primero). Si otra transacción ya creó una asignación
            // vigente para este activo, el índice único parcial hace fallar el INSERT:
            // ahí se decide, en la base de datos, quién gana la carrera.
            assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, ACTIVE_ASSIGNMENT_INDEX)) {
                throw lostTheRace(assetId);
            }
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw lostTheRace(assetId);
        }

        audit.record(actor.id(), AuditAction.ASSET_ASSIGNED, AuditEntityType.ASSET, asset.getId(),
                AuditMetadata.of("assetTag", asset.getAssetTag(), "assignedTo", assignee.getFullName()));
        notifications.notifyUsers(NotificationType.ASSET_ASSIGNED,
                "Asset %s (%s %s) was assigned to you".formatted(asset.getAssetTag(), asset.getBrand(),
                        asset.getModel()),
                null, actor.id(), List.of(assignee.getId()));

        return AssetResponse.from(asset, assignment);
    }

    @Transactional
    public AssetResponse transfer(UUID assetId, AssignAssetRequest request, AuthenticatedUser actor) {
        Asset asset = requireAsset(assetId);
        AssetAssignment current = currentAssignment(assetId).orElseThrow(() ->
                new BusinessException(ErrorCode.ASSET_NOT_ASSIGNED,
                        "The asset is not assigned to anyone. Use the assign endpoint instead."));
        User newHolder = requireActiveAssignee(request.userId());

        if (current.getUser().getId().equals(newHolder.getId())) {
            throw new BusinessException(ErrorCode.ASSET_ALREADY_ASSIGNED,
                    "The asset is already assigned to that user.");
        }

        AssetAssignment next = AssetAssignment.open(asset, newHolder,
                userRepository.getReferenceById(actor.id()), Texts.blankToNull(request.notes()));
        try {
            current.close();
            // El índice único exige que la vigente quede cerrada ANTES de insertar la nueva.
            // Sin este flush intermedio, Hibernate haría el INSERT primero y chocaría con ella.
            assignmentRepository.saveAndFlush(current);
            assignmentRepository.saveAndFlush(next);
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, ACTIVE_ASSIGNMENT_INDEX)) {
                throw lostTheRace(assetId);
            }
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw lostTheRace(assetId);
        }

        audit.record(actor.id(), AuditAction.ASSET_TRANSFERRED, AuditEntityType.ASSET, asset.getId(),
                AuditMetadata.of("assetTag", asset.getAssetTag(),
                        "from", current.getUser().getFullName(), "to", newHolder.getFullName()));
        notifications.notifyUsers(NotificationType.ASSET_TRANSFERRED,
                "Asset %s was transferred from %s to %s".formatted(asset.getAssetTag(),
                        current.getUser().getFullName(), newHolder.getFullName()),
                null, actor.id(), List.of(current.getUser().getId(), newHolder.getId()));

        return AssetResponse.from(asset, next);
    }

    @Transactional
    public AssetResponse returnAsset(UUID assetId, AuthenticatedUser actor) {
        Asset asset = requireAsset(assetId);
        AssetAssignment current = currentAssignment(assetId).orElseThrow(() ->
                new BusinessException(ErrorCode.ASSET_NOT_ASSIGNED, "The asset is not currently assigned."));

        current.close();
        asset.markAvailable();
        assignmentRepository.saveAndFlush(current);   // este flush también guarda el cambio de estado del activo

        audit.record(actor.id(), AuditAction.ASSET_RETURNED, AuditEntityType.ASSET, asset.getId(),
                AuditMetadata.of("assetTag", asset.getAssetTag(), "returnedBy", current.getUser().getFullName()));
        notifications.notifyUsers(NotificationType.ASSET_RETURNED,
                "Asset %s was marked as returned".formatted(asset.getAssetTag()),
                null, actor.id(), List.of(current.getUser().getId()));

        return AssetResponse.from(asset, null);
    }

    // Cambiar estado
    @Transactional
    public AssetResponse changeStatus(UUID assetId, ChangeAssetStatusRequest request, AuthenticatedUser actor) {
        Asset asset = requireAsset(assetId);
        AssetStatus from = asset.getStatus();
        AssetStatus target = request.status();

        if (target == AssetStatus.ASSIGNED) {
            throw new BusinessException(ErrorCode.INVALID_ASSET_STATUS_TRANSITION,
                    "Use the assign endpoint to assign an asset to a user.");
        }
        if (asset.getStatus() == AssetStatus.ASSIGNED) {
            throw new BusinessException(ErrorCode.INVALID_ASSET_STATUS_TRANSITION,
                    "Return the asset before changing its status.");
        }

        asset.changeStatus(target);   // el dominio lanza 409 si la transición no es válida
        assetRepository.saveAndFlush(asset);

        audit.record(actor.id(), AuditAction.ASSET_STATUS_CHANGED, AuditEntityType.ASSET, asset.getId(),
                AuditMetadata.of("assetTag", asset.getAssetTag(), "from", from, "to", target));

        return AssetResponse.from(asset, null);
    }

    // Helpers
    private Asset requireAsset(UUID id) {
        return assetRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
    }

    private Optional<AssetAssignment> currentAssignment(UUID assetId) {
        return assignmentRepository.findByAssetIdAndReturnedAtIsNull(assetId);
    }

    private AssetType findActiveType(UUID id) {
        return assetTypeRepository.findById(id)
                .filter(AssetType::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "The selected asset type does not exist or is inactive."));
    }

    private User requireActiveAssignee(UUID userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ASSIGNEE,
                        "The selected user does not exist or is inactive."));
    }

    private void ensureAssignable(Asset asset) {
        switch (asset.getStatus()) {
            case AVAILABLE -> {
            }
            case ASSIGNED -> {
                String holder = currentAssignment(asset.getId())
                        .map(a -> a.getUser().getFullName())
                        .orElse("another user");
                throw new BusinessException(ErrorCode.ASSET_ALREADY_ASSIGNED,
                        "The asset is already assigned to %s.".formatted(holder));
            }
            case MAINTENANCE -> throw new BusinessException(ErrorCode.ASSET_NOT_ASSIGNABLE,
                    "An asset under maintenance cannot be assigned.");
            case RETIRED -> throw new BusinessException(ErrorCode.ASSET_NOT_ASSIGNABLE,
                    "A retired asset cannot be assigned.");
        }
    }

    private void ensureSerialIsFree(String serial, UUID ownerId) {
        if (serial == null) {
            return;
        }
        boolean taken = ownerId == null
                ? assetRepository.existsBySerialNumber(serial)
                : assetRepository.existsBySerialNumberAndIdNot(serial, ownerId);
        if (taken) {
            throw duplicateSerial();
        }
    }

    private void saveCatchingDuplicateSerial(Asset asset) {
        try {
            assetRepository.saveAndFlush(asset);
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, SERIAL_INDEX)) {
                throw duplicateSerial();   // dos altas simultáneas con el mismo serial
            }
            throw ex;
        }
    }

    private BusinessException lostTheRace(UUID assetId) {
        log.info("Concurrent assignment on asset {}: another transaction won the race", assetId);
        return new BusinessException(ErrorCode.ASSET_ALREADY_ASSIGNED,
                "The asset was assigned by someone else at the same time.");
    }
}
