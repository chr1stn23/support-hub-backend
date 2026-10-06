package com.chrisdev.supporthub.user.api;

import com.chrisdev.supporthub.user.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserAdminResponse(
        UUID id,
        String email,
        String fullName,
        boolean active,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {

    public static UserAdminResponse from(User u) {
        return new UserAdminResponse(u.getId(), u.getEmail(), u.getFullName(), u.isActive(),
                u.roleNames(), u.getCreatedAt(), u.getUpdatedAt(), u.getVersion());
    }
}
