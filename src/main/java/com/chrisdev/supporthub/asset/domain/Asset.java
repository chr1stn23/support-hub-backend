package com.chrisdev.supporthub.asset.domain;

import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Generated // lo genera la BD (AST-000001)
    @Column(name = "asset_tag", nullable = false, updatable = false)
    private String assetTag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_type_id")
    private AssetType assetType;

    private String brand;
    private String model;

    @Column(name = "serial_number")
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status;

    private String location;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Asset() {
    }

    public static Asset register(AssetType type, String brand, String model,
            String serialNumber, String location) {
        Asset asset = new Asset();
        asset.assetType = type;
        asset.brand = brand;
        asset.model = model;
        asset.serialNumber = serialNumber;
        asset.location = location;
        asset.status = AssetStatus.AVAILABLE;
        return asset;
    }

    // Comportamiento de dominio
    public boolean isRetired() {
        return status == AssetStatus.RETIRED;
    }

    public void markAssigned() {
        transitionTo(AssetStatus.ASSIGNED);
    }

    public void markAvailable() {
        transitionTo(AssetStatus.AVAILABLE);
    }

    public void changeStatus(AssetStatus target) {
        transitionTo(target);
    }

    private void transitionTo(AssetStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_ASSET_STATUS_TRANSITION,
                    "An asset cannot move from %s to %s.".formatted(status, target));
        }
        this.status = target;
    }

    public void changeType(AssetType type) {
        this.assetType = type;
    }

    public void changeBrand(String brand) {
        this.brand = brand;
    }

    public void changeModel(String model) {
        this.model = model;
    }

    public void changeSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public void changeLocation(String location) {
        this.location = location;
    }

    public UUID getId() {
        return id;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public String getLocation() {
        return location;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
