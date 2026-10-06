package com.chrisdev.supporthub.user.infrastructure;

import com.chrisdev.supporthub.user.domain.RoleName;
import com.chrisdev.supporthub.user.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(UUID id);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u.id FROM User u JOIN u.roles r WHERE r.name = :role AND u.active = true")
    List<UUID> findActiveIdsByRole(@Param("role") RoleName role);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :role AND u.active = true")
    long countActiveByRole(@Param("role") RoleName role);
}
