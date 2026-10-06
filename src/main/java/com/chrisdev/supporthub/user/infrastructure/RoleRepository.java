package com.chrisdev.supporthub.user.infrastructure;

import com.chrisdev.supporthub.user.domain.Role;
import com.chrisdev.supporthub.user.domain.RoleName;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(RoleName name);

    List<Role> findByNameIn(Collection<RoleName> names);

    /**
     * SELECT ... FOR UPDATE: serializa las operaciones que pueden dejar el sistema sin admins.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.name = :name")
    Optional<Role> findByNameForUpdate(@Param("name") RoleName name);
}
