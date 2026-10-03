package com.chrisdev.supporthub.common.security;

import com.chrisdev.supporthub.user.domain.RoleName;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(UUID id, Set<String> roles) {

    public static AuthenticatedUser from(Jwt jwt) {
        List<String> claim = jwt.getClaimAsStringList("roles");
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                claim == null ? Set.of() : Set.copyOf(claim));
    }

    public boolean hasRole(RoleName role) {
        return roles.contains(role.name());
    }
}
