package com.chrisdev.supporthub.asset.infrastructure;

import com.chrisdev.supporthub.asset.domain.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    @Override
    @EntityGraph(attributePaths = "assetType")
    Page<Asset> findAll(Specification<Asset> spec, Pageable pageable);

    @EntityGraph(attributePaths = "assetType")
    Optional<Asset> findDetailById(UUID id);

    boolean existsBySerialNumber(String serialNumber);

    boolean existsBySerialNumberAndIdNot(String serialNumber, UUID id);
}
