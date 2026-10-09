package com.chrisdev.supporthub.auth.application;

import com.chrisdev.supporthub.audit.application.AuditMetadata;
import com.chrisdev.supporthub.audit.application.AuditService;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.exception.InvalidCredentialsException;
import com.chrisdev.supporthub.user.api.UserResponse;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final AuditService auditService;
    private final LoginThrottle throttle;
    private final JdbcClient jdbc;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, SessionService sessions,
            AuditService auditService, LoginThrottle throttle, JdbcClient jdbc) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.auditService = auditService;
        this.throttle = throttle;
        this.jdbc = jdbc;
        // Hash de relleno: si el email no existe igual hacemos una comparación,
        // para que el tiempo de respuesta no delate qué emails existen.
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public SessionService.Session login(String email, String rawPassword, String clientIp) {
        String normalizedEmail = email.trim();
        throttle.check(normalizedEmail, clientIp); // Antes de gastar tiempo en Bcrypt

        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        String hashToCheck = (user != null) ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCheck);

        if (user == null || !passwordMatches || !user.isActive()) {
            throttle.recordFailure(normalizedEmail, clientIp);
            String reason = user == null ? "UNKNOWN_USER" : !passwordMatches ? "BAD_PASSWORD" : "INACTIVE_USER";
            UUID userId = user == null ? null : user.getId();
            auditService.recordFailure(userId, AuditAction.LOGIN_FAILED, AuditEntityType.USER, userId,
                    AuditMetadata.of("email", normalizedEmail, "ip", clientIp, "reason", reason));
            throw new InvalidCredentialsException();
        }

        throttle.reset(normalizedEmail);
        // Por SQL para que no cuente como una edición de usuario
        jdbc.sql("UPDATE users SET last_login_at = now() WHERE id = :id").param("id", user.getId()).update();

        SessionService.Session session = sessions.open(user);
        auditService.record(user.getId(), AuditAction.LOGIN_SUCCESS, AuditEntityType.USER, user.getId(),
                AuditMetadata.of("ip", clientIp));
        return session;
    }

    @Transactional
    public SessionService.Session refresh(String rawRefreshToken) {
        return sessions.rotate(rawRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        sessions.close(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        User user = userRepository.findWithRolesById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "The authenticated user no longer exists or is inactive."));
        return UserResponse.from(user);
    }
}
