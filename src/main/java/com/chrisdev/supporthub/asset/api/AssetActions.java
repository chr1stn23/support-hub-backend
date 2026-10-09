package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.domain.AssetStatus;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.user.domain.RoleName;

import java.util.List;

/**
 * Qué puede hacer QUIEN consulta sobre este equipo. Solo ADMIN y ASSET_MANAGER gestionan inventario.
 */
public record AssetActions(
        boolean edit,
        boolean assign,
        boolean transfer,
        boolean returnAsset,
        List<AssetStatus> changeStatus      // ASSIGNED nunca aparece: se llega con assign y se sale con return
) {

    private static final AssetActions NONE = new AssetActions(false, false, false, false, List.of());

    public static AssetActions of(Asset asset, AuthenticatedUser actor) {
        boolean manager = actor.hasRole(RoleName.ADMIN) || actor.hasRole(RoleName.ASSET_MANAGER);
        return manager ? forManager(asset) : NONE;
    }

    static AssetActions forManager(Asset asset) {
        AssetStatus status = asset.getStatus();
        List<AssetStatus> targets = status == AssetStatus.ASSIGNED
                ? List.of()
                : status.allowedTransitions().stream().filter(t -> t != AssetStatus.ASSIGNED).toList();
        return new AssetActions(
                !asset.isRetired(),
                status == AssetStatus.AVAILABLE,
                status == AssetStatus.ASSIGNED,
                status == AssetStatus.ASSIGNED,
                targets);
    }
}