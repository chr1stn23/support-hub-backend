package com.chrisdev.supporthub.user.infrastructure;

import com.chrisdev.supporthub.user.domain.Role;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.chrisdev.supporthub.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "Demo1234!";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("admin@supporthub.local", "Ana Admin", RoleName.ADMIN);
        seed("agent@supporthub.local", "Carlos Agente", RoleName.AGENT);
        seed("user@supporthub.local", "Lucía Usuaria", RoleName.USER);
        seed("assets@supporthub.local", "Mario Activos", RoleName.ASSET_MANAGER);
    }

    private void seed(String email, String fullName, RoleName roleName) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(email, passwordEncoder.encode(DEMO_PASSWORD), fullName);
        user.addRole(role);
        userRepository.save(user);
        log.info("Demo user created: {} ({})", email, roleName);
    }
}
