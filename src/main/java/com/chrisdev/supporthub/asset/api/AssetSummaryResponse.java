package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.domain.AssetAssignment;
import com.chrisdev.supporthub.asset.domain.AssetStatus;
import com.chrisdev.supporthub.user.api.UserSummary;

import java.util.UUID;

/**
 * Fila del listado. Distinta de AssetSummary, la referencia mínima que va dentro de un ticket.
 */
public record AssetSummaryResponse(
        UUID id,
        String assetTag,
        String type,
        String brand,
        String model,
        AssetStatus status,
        String location,
        UserSummary assignedTo
) {

    public static AssetSummaryResponse from(Asset a, AssetAssignment current) {
        return new AssetSummaryResponse(
                a.getId(), a.getAssetTag(), a.getAssetType().getName(),
                a.getBrand(), a.getModel(), a.getStatus(), a.getLocation(),
                current == null ? null : UserSummary.from(current.getUser()));
    }
}