package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.domain.AssetAssignment;
import com.chrisdev.supporthub.asset.domain.AssetStatus;
import com.chrisdev.supporthub.user.api.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String assetTag,
        AssetTypeResponse type,
        String brand,
        String model,
        String serialNumber,
        AssetStatus status,
        String location,
        UserSummary assignedTo,
        Instant assignedSince,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        AssetActions actions
) {

    /**
     * current = asignación vigente, o null si el activo no está asignado.
     */
    public static AssetResponse from(Asset a, AssetAssignment current, AssetActions actions) {
        return new AssetResponse(
                a.getId(), a.getAssetTag(), AssetTypeResponse.from(a.getAssetType()),
                a.getBrand(), a.getModel(), a.getSerialNumber(), a.getStatus(), a.getLocation(),
                current == null ? null : UserSummary.from(current.getUser()),
                current == null ? null : current.getAssignedAt(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getVersion(), actions);
    }
}