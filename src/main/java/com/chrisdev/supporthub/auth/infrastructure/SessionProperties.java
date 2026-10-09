package com.chrisdev.supporthub.auth.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(
        boolean cookieSecure,
        @NotBlank String sameSite,
        @Positive int refreshIdleDays,
        @Positive int refreshAbsoluteDays,
        boolean exposeTokenInBody,
        @Valid @NotNull Throttle throttle
) {

    public record Throttle(
            @Positive int maxFailuresPerEmail,
            @Positive int maxFailuresPerIp,
            @Positive int windowMinutes
    ) {
    }
}
