package com.chrisdev.supporthub.user.api;

import com.chrisdev.supporthub.user.domain.User;

import java.util.UUID;

public record UserSummary(UUID id, String fullName) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName());
    }
}
