package com.chrisdev.supporthub.asset.infrastructure;

import com.chrisdev.supporthub.asset.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
}
