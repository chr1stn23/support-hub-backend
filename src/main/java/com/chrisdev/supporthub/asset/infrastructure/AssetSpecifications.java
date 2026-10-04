package com.chrisdev.supporthub.asset.infrastructure;

import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.domain.AssetStatus;
import com.chrisdev.supporthub.common.util.Texts;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class AssetSpecifications {

    private AssetSpecifications() {
    }

    public static Specification<Asset> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Asset> hasStatus(AssetStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Asset> hasType(UUID assetTypeId) {
        return (root, query, cb) -> cb.equal(root.get("assetType").get("id"), assetTypeId);
    }

    public static Specification<Asset> matches(String text) {
        return (root, query, cb) -> {
            String pattern = "%" + Texts.escapeLike(text.toLowerCase()) + "%";
            return cb.or(
                    cb.like(cb.lower(root.<String>get("assetTag")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("brand")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("model")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("serialNumber")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("location")), pattern, '\\'));
        };
    }
}
