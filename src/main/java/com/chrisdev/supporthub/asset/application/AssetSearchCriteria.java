package com.chrisdev.supporthub.asset.application;

import com.chrisdev.supporthub.asset.domain.AssetStatus;

import java.util.UUID;

public record AssetSearchCriteria(AssetStatus status, UUID assetTypeId, String search) {
}
