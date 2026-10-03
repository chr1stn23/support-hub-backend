package com.chrisdev.supporthub.auth.api;

import com.chrisdev.supporthub.user.api.UserResponse;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
