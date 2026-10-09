package com.chrisdev.supporthub.auth.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public class SecurityStampValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult REVOKED = OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "The session is no longer valid.", null));
    private final JdbcClient jdbc;

    public SecurityStampValidator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            return REVOKED;
        }
        String stamp = jwt.getClaimAsString("stamp");
        if (stamp == null) {
            return REVOKED;
        }

        return jdbc.sql("SELECT security_stamp, active FROM users WHERE id = :id")
                .param("id", userId)
                .query((rs, n) -> new Row(rs.getObject("security_stamp", UUID.class)
                        .toString(), rs.getBoolean("active")))
                .optional()
                .filter(row -> row.active() && row.stamp().equals(stamp))
                .map(row -> OAuth2TokenValidatorResult.success())
                .orElse(REVOKED);
    }

    private record Row(String stamp, boolean active) {
    }
}
