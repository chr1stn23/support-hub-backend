package com.chrisdev.supporthub.asset.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;

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

    private String brand;
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status;

    protected Asset() {
    }

    public UUID getId() {
        return id;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public AssetStatus getStatus() {
        return status;
    }
}
