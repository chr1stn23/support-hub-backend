package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.domain.Asset;

import java.util.UUID;

public record AssetSummary(UUID id, String assetTag, String brand, String model) {

    public static AssetSummary from(Asset asset) {
        return asset == null ? null
                : new AssetSummary(asset.getId(), asset.getAssetTag(), asset.getBrand(), asset.getModel());
    }
}