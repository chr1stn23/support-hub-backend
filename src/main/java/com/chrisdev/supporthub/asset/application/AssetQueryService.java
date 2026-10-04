package com.chrisdev.supporthub.asset.application;

import com.chrisdev.supporthub.asset.api.AssetResponse;
import com.chrisdev.supporthub.asset.api.AssetSummaryResponse;
import com.chrisdev.supporthub.asset.api.AssetTypeResponse;
import com.chrisdev.supporthub.asset.api.AssignmentResponse;
import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.domain.AssetAssignment;
import com.chrisdev.supporthub.asset.infrastructure.AssetAssignmentRepository;
import com.chrisdev.supporthub.asset.infrastructure.AssetRepository;
import com.chrisdev.supporthub.asset.infrastructure.AssetSpecifications;
import com.chrisdev.supporthub.asset.infrastructure.AssetTypeRepository;
import com.chrisdev.supporthub.common.api.PageResponse;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.exception.ResourceNotFoundException;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AssetQueryService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("assetTag", "brand", "model", "createdAt", "updatedAt");
    private static final int MAX_SEARCH_LENGTH = 100;

    private final AssetRepository assetRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final AssetAssignmentRepository assignmentRepository;

    public AssetQueryService(AssetRepository assetRepository,
            AssetTypeRepository assetTypeRepository,
            AssetAssignmentRepository assignmentRepository) {
        this.assetRepository = assetRepository;
        this.assetTypeRepository = assetTypeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public PageResponse<AssetSummaryResponse> search(AssetSearchCriteria criteria, Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Cannot sort by '%s'. Allowed fields: %s."
                                .formatted(order.getProperty(), SORTABLE_FIELDS));
            }
        }

        Specification<Asset> spec = AssetSpecifications.all();
        if (criteria.status() != null) {
            spec = spec.and(AssetSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.assetTypeId() != null) {
            spec = spec.and(AssetSpecifications.hasType(criteria.assetTypeId()));
        }
        if (criteria.search() != null && !criteria.search().isBlank()) {
            String text = criteria.search().trim();
            if (text.length() > MAX_SEARCH_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "The search text cannot exceed %d characters.".formatted(MAX_SEARCH_LENGTH));
            }
            spec = spec.and(AssetSpecifications.matches(text));
        }

        Page<Asset> page = assetRepository.findAll(spec, pageable);

        // Responsables de TODA la página en una sola consulta
        List<UUID> ids = page.getContent().stream().map(Asset::getId).toList();
        Map<UUID, AssetAssignment> holders = ids.isEmpty() ? Map.of()
                : assignmentRepository.findByAssetIdInAndReturnedAtIsNull(ids).stream()
                .collect(Collectors.toMap(a -> a.getAsset().getId(), Function.identity()));

        return PageResponse.from(page, a -> AssetSummaryResponse.from(a, holders.get(a.getId())));
    }

    public AssetResponse getById(UUID id) {
        Asset asset = assetRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
        AssetAssignment current = assignmentRepository.findByAssetIdAndReturnedAtIsNull(id).orElse(null);
        return AssetResponse.from(asset, current);
    }

    public List<AssignmentResponse> getAssignments(UUID assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset", assetId);
        }
        return assignmentRepository.findByAssetIdOrderByAssignedAtDesc(assetId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    public List<AssetSummaryResponse> myAssets(AuthenticatedUser user) {
        return assignmentRepository.findByUserIdAndReturnedAtIsNull(user.id()).stream()
                .map(a -> AssetSummaryResponse.from(a.getAsset(), a))
                .toList();
    }

    public List<AssetTypeResponse> listTypes() {
        return assetTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(AssetTypeResponse::from)
                .toList();
    }
}
