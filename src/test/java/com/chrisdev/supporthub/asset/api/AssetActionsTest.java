package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.domain.AssetStatus;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.support.TestEntities;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AssetActionsTest {

    private static Asset assetIn(AssetStatus status) {
        Asset asset = Asset.register(TestEntities.assetType(), "Dell", "Latitude", null, null);
        switch (status) {
            case AVAILABLE -> {
            }
            case ASSIGNED -> asset.markAssigned();
            case MAINTENANCE, RETIRED -> asset.changeStatus(status);
        }
        return asset;
    }

    private static AuthenticatedUser user(String role) {
        return new AuthenticatedUser(UUID.randomUUID(), Set.of(role));
    }

    @Test
    void anAvailableAssetCanBeAssignedEditedAndSentToMaintenanceOrRetired() {
        AssetActions actions = AssetActions.of(assetIn(AssetStatus.AVAILABLE), user("ASSET_MANAGER"));
        assertThat(actions.edit()).isTrue();
        assertThat(actions.assign()).isTrue();
        assertThat(actions.transfer()).isFalse();
        assertThat(actions.returnAsset()).isFalse();
        assertThat(actions.changeStatus()).containsExactly(AssetStatus.MAINTENANCE, AssetStatus.RETIRED);
    }

    @Test
    void anAssignedAssetCanOnlyBeTransferredOrReturned() {
        AssetActions actions = AssetActions.of(assetIn(AssetStatus.ASSIGNED), user("ADMIN"));
        assertThat(actions.assign()).isFalse();
        assertThat(actions.transfer()).isTrue();
        assertThat(actions.returnAsset()).isTrue();
        assertThat(actions.changeStatus()).isEmpty();
    }

    @Test
    void anAssetUnderMaintenanceCanBeReleasedOrRetired() {
        AssetActions actions = AssetActions.of(assetIn(AssetStatus.MAINTENANCE), user("ASSET_MANAGER"));
        assertThat(actions.assign()).isFalse();
        assertThat(actions.changeStatus()).containsExactly(AssetStatus.AVAILABLE, AssetStatus.RETIRED);
    }

    @Test
    void aRetiredAssetOffersNothing() {
        AssetActions actions = AssetActions.of(assetIn(AssetStatus.RETIRED), user("ADMIN"));
        assertThat(actions.edit()).isFalse();
        assertThat(actions.assign()).isFalse();
        assertThat(actions.changeStatus()).isEmpty();
    }

    @Test
    void peopleWhoDoNotManageInventoryGetNoActions() {
        AssetActions actions = AssetActions.of(assetIn(AssetStatus.AVAILABLE), user("AGENT"));
        assertThat(actions.edit()).isFalse();
        assertThat(actions.assign()).isFalse();
        assertThat(actions.changeStatus()).isEmpty();
    }
}
