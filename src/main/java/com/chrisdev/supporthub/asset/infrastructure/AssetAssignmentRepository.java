package com.chrisdev.supporthub.asset.infrastructure;

import com.chrisdev.supporthub.asset.domain.AssetAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, UUID> {

    // A lo sumo 1 por activo: lo garantiza el índice único parcial de la BD
    @EntityGraph(attributePaths = "asset")
    Optional<AssetAssignment> findByAssetIdAndReturnedAtIsNull(UUID assetId);

    // Para armar una página de activos con su responsable en una consulta (evitar N+1)
    @EntityGraph(attributePaths = "user")
    List<AssetAssignment> findByAssetIdInAndReturnedAtIsNull(Collection<UUID> assetIds);

    @EntityGraph(attributePaths = {"user", "assignedBy"})
    List<AssetAssignment> findByAssetIdOrderByAssignedAtDesc(UUID assetId);

    @EntityGraph(attributePaths = {"user", "asset", "asset.assetType"})
    List<AssetAssignment> findByUserIdAndReturnedAtIsNull(UUID userId);

    long countByUserIdAndReturnedAtIsNull(UUID userId);

    boolean existsByAssetIdAndUserIdAndReturnedAtIsNull(UUID assetId, UUID userId);
}
