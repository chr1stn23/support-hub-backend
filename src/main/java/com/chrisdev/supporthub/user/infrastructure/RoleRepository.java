package com.chrisdev.supporthub.user.infrastructure;

import com.chrisdev.supporthub.user.domain.Role;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(RoleName name);
}
