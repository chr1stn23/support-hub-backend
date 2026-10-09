package com.chrisdev.supporthub.auth.infrastructure;

import com.chrisdev.supporthub.auth.application.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class AuthCookies {

    public static final String ACCESS = "SH_ACCESS";
    public static final String REFRESH = "SH_REFRESH";
    public static final String XSRF = "XSRF-TOKEN";

    // El access solo viaja a la API; el refresh, únicamente a los endpoints de autenticación
    private static final String ACCESS_PATH = "/api";
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final SessionProperties props;

    public AuthCookies(SessionProperties props) {
        this.props = props;
    }

    public static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private static Duration remaining(Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public ResponseCookie access(String token, Instant expiresAt) {
        return build(ACCESS, token, ACCESS_PATH, true, remaining(expiresAt));
    }

    public ResponseCookie refresh(String token, Instant expiresAt) {
        return build(REFRESH, token, REFRESH_PATH, true, remaining(expiresAt));
    }

    /**
     * No es HttpOnly a propósito: Angular la lee para devolverla en la cabecera X-XSRF-TOKEN.
     */
    public ResponseCookie xsrf(String token) {
        return build(XSRF, token, "/", false, Duration.ofDays(props.refreshAbsoluteDays()));
    }

    public List<ResponseCookie> clearSession() {
        return List.of(
                build(ACCESS, "", ACCESS_PATH, true, Duration.ZERO),
                build(REFRESH, "", REFRESH_PATH, true, Duration.ZERO));
    }

    public HttpHeaders sessionHeaders(SessionService.Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, access(session.accessToken(), session.accessExpiresAt()).toString());
        headers.add(HttpHeaders.SET_COOKIE, refresh(session.refreshToken(), session.refreshExpiresAt()).toString());
        return headers;
    }

    private ResponseCookie build(String name, String value, String path, boolean httpOnly, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(props.cookieSecure())
                .sameSite(props.sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

}
