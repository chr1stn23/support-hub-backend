package com.chrisdev.supporthub.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE) String newPassword
) {
}