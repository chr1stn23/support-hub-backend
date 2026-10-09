package com.chrisdev.supporthub.auth.api;

import com.chrisdev.supporthub.auth.application.AuthService;
import com.chrisdev.supporthub.auth.application.SessionService;
import com.chrisdev.supporthub.auth.infrastructure.AuthCookies;
import com.chrisdev.supporthub.auth.infrastructure.SessionProperties;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.user.api.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookies cookies;
    private final SessionProperties props;

    public AuthController(AuthService authService, AuthCookies cookies, SessionProperties props) {
        this.authService = authService;
        this.cookies = cookies;
        this.props = props;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withSession(authService.login(request.email(), request.password(), http.getRemoteAddr()));
    }

    /**
     * Renueva la sesión con la cookie de refresh. Si ya no vale, limpia las cookies del navegador.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest http, HttpServletResponse response) {
        String token = AuthCookies.read(http, AuthCookies.REFRESH)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "No refresh token."));
        try {
            return withSession(authService.refresh(token));
        } catch (BusinessException ex) {
            if (ex.getCode() == ErrorCode.UNAUTHORIZED) {
                cookies.clearSession().forEach(c -> response.addHeader(HttpHeaders.SET_COOKIE, c.toString()));
            }
            throw ex;
        }
    }

    /**
     * Idempotente: sin cookie o con una cookie desconocida también responde 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        AuthCookies.read(http, AuthCookies.REFRESH).ifPresent(authService::logout);
        HttpHeaders headers = new HttpHeaders();
        cookies.clearSession().forEach(c -> headers.add(HttpHeaders.SET_COOKIE, c.toString()));
        return ResponseEntity.noContent().headers(headers).build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(UUID.fromString(jwt.getSubject()));
    }

    private ResponseEntity<LoginResponse> withSession(SessionService.Session session) {
        long expiresIn = Math.max(0, Duration.between(Instant.now(), session.accessExpiresAt()).toSeconds());
        String bodyToken = props.exposeTokenInBody() ? session.accessToken() : null;
        return ResponseEntity.ok()
                .headers(cookies.sessionHeaders(session))
                .body(new LoginResponse(bodyToken, "Bearer", expiresIn, session.user()));
    }
}
