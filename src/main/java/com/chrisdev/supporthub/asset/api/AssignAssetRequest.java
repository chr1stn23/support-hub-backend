package com.chrisdev.supporthub.asset.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Se usa tanto para asignar como para transferir.
 */
public record AssignAssetRequest(
        @NotNull UUID userId,
        @Size(max = 500) String notes
) {
}