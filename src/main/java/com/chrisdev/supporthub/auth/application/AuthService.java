    package com.chrisdev.supporthub.auth.application;

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
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        // Hash de relleno: si el email no existe igual hacemos una comparación,
        // para que el tiempo de respuesta no delate qué emails existen.
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);

        String hashToCheck = (user != null) ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCheck);

        if (user == null || !passwordMatches || !user.isActive()) {
            throw new InvalidCredentialsException();
        }

        JwtService.IssuedToken token = jwtService.issue(user);
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
