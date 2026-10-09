package com.chrisdev.supporthub.auth.application;

import com.chrisdev.supporthub.audit.application.AuditMetadata;
import com.chrisdev.supporthub.audit.application.AuditService;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.auth.infrastructure.JwtService;
import com.chrisdev.supporthub.auth.infrastructure.RefreshTokenStore;
import com.chrisdev.supporthub.auth.infrastructure.SessionProperties;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.user.api.UserResponse;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class SessionService {

    private static final Duration RACE_GRACE = Duration.ofSeconds(10);

    private final RefreshTokenStore store;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuditService audit;
    private final SessionProperties props;
    private final SecureRandom random = new SecureRandom();

    public SessionService(RefreshTokenStore store, JwtService jwtService, UserRepository userRepository,
            AuditService audit, SessionProperties props) {
        this.store = store;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.audit = audit;
        this.props = props;
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "The refresh token is invalid or expired.");
    }

    /**
     * Sesión nueva (login): empieza una familia de refresh tokens.
     */
    @Transactional
    public Session open(User user) {
        Instant now = Instant.now();
        Instant familyExpiresAt = now.plus(Duration.ofDays(props.refreshAbsoluteDays()));
        return issue(user, UUID.randomUUID(), familyExpiresAt, now);
    }

    /**
     * Cambia un refresh token por un par nuevo e invalida el usado.
     */
    @Transactional
    public Session rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshTokenStore.Row row = store.findByHash(hash(rawToken)).orElseThrow(SessionService::unauthorized);

        if (row.revokedAt() != null || !row.expiresAt().isAfter(now) || !row.familyExpiresAt().isAfter(now)) {
            throw unauthorized();
        }

        if (row.usedAt() != null) {
            if (row.usedAt().isAfter(now.minus(RACE_GRACE))) {
                // Otra pestaña acaba de renovar: sus cookies nuevas ya están en el navegador
                throw new BusinessException(ErrorCode.CONCURRENT_REFRESH, "A refresh with this token is already in " +
                        "progress.");
            }
            // Un token ya consumido que reaparece más tarde: alguien lo copió. Se cae toda la familia
            store.revokeFamilyNow(row.familyId());
            audit.recordFailure(row.userId(), AuditAction.REFRESH_REUSE_DETECTED, AuditEntityType.USER, row.userId(),
                    AuditMetadata.of("familyId", row.familyId()));
            throw unauthorized();
        }

        if (!store.markUsed(row.id())) {
            throw new BusinessException(ErrorCode.CONCURRENT_REFRESH, "A refresh with this token is already in " +
                    "progress.");
        }

        User user = userRepository.findWithRolesById(row.userId())
                .filter(User::isActive)
                .orElseThrow(SessionService::unauthorized);
        return issue(user, row.familyId(), row.familyExpiresAt(), now);
    }

    /**
     * Logout: revoca toda la familia del refresh token presentado. Idempotente.
     */
    @Transactional
    public void close(String rawToken) {
        store.findByHash(hash(rawToken)).ifPresent(row -> {
            store.revokeFamily(row.familyId());
            audit.record(row.userId(), AuditAction.LOGOUT, AuditEntityType.USER, row.userId(), null);
        });
    }

    /**
     * Cambio de contraseña o baja: ninguna sesión de esa persona sigue viva.
     */
    @Transactional
    public void revokeAllFor(UUID userId) {
        store.revokeAllForUser(userId);
    }

    private Session issue(User user, UUID familyId, Instant familyExpiresAt, Instant now) {
        JwtService.IssuedToken access = jwtService.issue(user);

        String refresh = newToken();
        Instant idleLimit = now.plus(Duration.ofDays(props.refreshIdleDays()));
        Instant refreshExpiresAt = idleLimit.isBefore(familyExpiresAt) ? idleLimit : familyExpiresAt;
        store.insert(user.getId(), familyId, hash(refresh), refreshExpiresAt, familyExpiresAt);

        return new Session(UserResponse.from(user), access.value(), access.expiresAt(), refresh, refreshExpiresAt);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Session(UserResponse user, String accessToken, Instant accessExpiresAt,
                          String refreshToken, Instant refreshExpiresAt) {
    }
}
