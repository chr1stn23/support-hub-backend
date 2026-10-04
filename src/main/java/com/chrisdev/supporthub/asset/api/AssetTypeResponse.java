package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.domain.AssetType;

import java.util.UUID;

public record AssetTypeResponse(UUID id, String name) {

    public static AssetTypeResponse from(AssetType type) {
        return new AssetTypeResponse(type.getId(), type.getName());
    }
}