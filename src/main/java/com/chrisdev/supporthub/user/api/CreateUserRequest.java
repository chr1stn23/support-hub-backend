package com.chrisdev.supporthub.user.api;

import com.chrisdev.supporthub.user.domain.RoleName;
import jakarta.validation.constraints.*;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE) String password,
        @NotEmpty Set<RoleName> roles
) {
}
