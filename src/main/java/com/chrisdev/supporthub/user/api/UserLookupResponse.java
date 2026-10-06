package com.chrisdev.supporthub.user.api;

import com.chrisdev.supporthub.user.domain.User;

import java.util.UUID;

/**
 * Lo mínimo para un selector: sin roles, sin estado, sin fechas.
 */
public record UserLookupResponse(UUID id, String fullName, String email) {

    public static UserLookupResponse from(User u) {
        return new UserLookupResponse(u.getId(), u.getFullName(), u.getEmail());
    }
}