package com.chrisdev.supporthub.user.infrastructure;

import com.chrisdev.supporthub.audit.application.AuditMetadata;
import com.chrisdev.supporthub.audit.application.AuditService;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.chrisdev.supporthub.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Crea el primer ADMIN desde variables de entorno, solo si todavía no existe ninguno activo.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final String email;
    private final String password;
    private final String fullName;

    public AdminBootstrap(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder, AuditService audit,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.bootstrap-admin.full-name:Administrator}") String fullName) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;   // no configurado: no hace nada
        }
        if (userRepository.countActiveByRole(RoleName.ADMIN) > 0) {
            log.info("An active admin already exists; bootstrap skipped.");
            return;
        }
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be between 8 and 72 bytes long");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("The bootstrap email belongs to an existing non-admin user; bootstrap skipped.");
            return;
        }

        User admin = new User(normalizedEmail, passwordEncoder.encode(password),
                fullName.isBlank() ? "Administrator" : fullName.trim());
        admin.addRole(roleRepository.findByName(RoleName.ADMIN).orElseThrow());
        userRepository.saveAndFlush(admin);

        audit.record(null, AuditAction.USER_CREATED, AuditEntityType.USER, admin.getId(),
                AuditMetadata.of("email", normalizedEmail, "source", "bootstrap"));
        log.info("Bootstrap admin created: {}", normalizedEmail);
    }
}
