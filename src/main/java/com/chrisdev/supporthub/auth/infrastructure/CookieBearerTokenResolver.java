package com.chrisdev.supporthub.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import java.util.Set;

public class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> UNAUTHENTICATED_PATHS =
            Set.of("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout");

    @Override
    public String resolve(HttpServletRequest request) {
        if (UNAUTHENTICATED_PATHS.contains(request.getRequestURI())) {
            return null;
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return AuthCookies.read(request, AuthCookies.ACCESS).orElse(null);
    }
}
