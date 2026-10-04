package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.domain.AssetStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeAssetStatusRequest(
        @NotNull AssetStatus status
) {
}
