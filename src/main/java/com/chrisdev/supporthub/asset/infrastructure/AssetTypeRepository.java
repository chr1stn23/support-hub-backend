package com.chrisdev.supporthub.asset.infrastructure;

import com.chrisdev.supporthub.asset.domain.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetTypeRepository extends JpaRepository<AssetType, UUID> {
    List<AssetType> findByActiveTrueOrderByNameAsc();
}
