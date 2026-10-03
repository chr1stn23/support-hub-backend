package com.chrisdev.supporthub.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 128) String password
) {
}
