package com.chrisdev.supporthub.asset.domain;

import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.support.TestEntities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AssetTest {

    private static void assertRejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_ASSET_STATUS_TRANSITION));
    }

    private Asset newAsset() {
        return Asset.register(TestEntities.assetType(), "Dell", "Latitude 5440", "SN-1", "Lima");
    }

    @Test
    void aNewAssetIsAvailable() {
        assertThat(newAsset().getStatus()).isEqualTo(AssetStatus.AVAILABLE);
    }

    @Test
    void anAssetCanBeAssignedAndReturned() {
        Asset asset = newAsset();
        asset.markAssigned();
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ASSIGNED);
        asset.markAvailable();
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.AVAILABLE);
    }

    @Test
    void anAssignedAssetCannotBeAssignedAgainNorSentToMaintenance() {
        Asset asset = newAsset();
        asset.markAssigned();
        assertRejected(asset::markAssigned);
        assertRejected(() -> asset.changeStatus(AssetStatus.MAINTENANCE));
    }

    @Test
    void anAssetUnderMaintenanceCannotBeAssigned() {
        Asset asset = newAsset();
        asset.changeStatus(AssetStatus.MAINTENANCE);
        assertRejected(asset::markAssigned);
    }

    @Test
    void retirementIsFinal() {
        Asset asset = newAsset();
        asset.changeStatus(AssetStatus.RETIRED);

        assertThat(asset.isRetired()).isTrue();
        assertRejected(asset::markAssigned);
        assertRejected(asset::markAvailable);
    }
}
