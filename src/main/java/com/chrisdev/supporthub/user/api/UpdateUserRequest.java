package com.chrisdev.supporthub.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotNull Long version,
        @Size(max = 150) String fullName,
        @Email @Size(max = 255) String email
) {

    public boolean hasChanges() {
        return fullName != null || email != null;
    }
}
