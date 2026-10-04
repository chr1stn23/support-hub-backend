package com.chrisdev.supporthub.asset.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateAssetRequest(
        @NotNull Long version,
        UUID assetTypeId,
        @Size(max = 80) String brand,
        @Size(max = 120) String model,
        @Size(max = 120) String serialNumber,
        @Size(max = 120) String location
) {

    public boolean hasChanges() {
        return assetTypeId != null || brand != null || model != null
                || serialNumber != null || location != null;
    }
}
