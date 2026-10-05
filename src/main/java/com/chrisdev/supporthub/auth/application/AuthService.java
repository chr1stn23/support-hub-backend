package com.chrisdev.supporthub.auth.application;

import com.chrisdev.supporthub.audit.application.AuditMetadata;
import com.chrisdev.supporthub.audit.application.AuditService;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.auth.api.LoginResponse;
import com.chrisdev.supporthub.auth.infrastructure.JwtService;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.exception.InvalidCredentialsException;
import com.chrisdev.supporthub.user.api.UserResponse;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.jwtService = jwtService;
        // Hash de relleno: si el email no existe igual hacemos una comparación,
        // para que el tiempo de respuesta no delate qué emails existen.
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public LoginResponse login(String email, String rawPassword, String clientIp) {
        String normalizedEmail = email.trim();
        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);

        String hashToCheck = (user != null) ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCheck);

        if (user == null || !passwordMatches || !user.isActive()) {
            // El motivo real solo queda en la auditoría; al cliente siempre se le dice lo mismo
            String reason = user == null ? "UNKNOWN_USER" : !passwordMatches ? "BAD_PASSWORD" : "INACTIVE_USER";
            UUID userId = user == null ? null : user.getId();
            auditService.recordFailure(userId, AuditAction.LOGIN_FAILED, AuditEntityType.USER, userId,
                    AuditMetadata.of("email", normalizedEmail, "ip", clientIp, "reason", reason));
            throw new InvalidCredentialsException();
        }

        JwtService.IssuedToken token = jwtService.issue(user);
        auditService.record(user.getId(), AuditAction.LOGIN_SUCCESS, AuditEntityType.USER, user.getId(),
                AuditMetadata.of("ip", clientIp));

        long expiresIn = Duration.between(Instant.now(), token.expiresAt()).toSeconds();
        return new LoginResponse(token.value(), "Bearer", expiresIn, UserResponse.from(user));
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
