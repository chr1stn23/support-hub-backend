package com.chrisdev.supporthub.asset.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAssetRequest(
        @NotNull UUID assetTypeId,
        @NotBlank @Size(max = 80) String brand,
        @NotBlank @Size(max = 120) String model,
        @Size(max = 120) String serialNumber,
        @Size(max = 120) String location
) {
}
