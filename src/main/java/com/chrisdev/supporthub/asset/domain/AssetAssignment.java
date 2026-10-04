package com.chrisdev.supporthub.asset.domain;

import com.chrisdev.supporthub.user.domain.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_assignments")
public class AssetAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(length = 500)
    private String notes;

    protected AssetAssignment() {
    }

    public static AssetAssignment open(Asset asset, User user, User assignedBy, String notes) {
        AssetAssignment a = new AssetAssignment();
        a.asset = asset;
        a.user = user;
        a.assignedBy = assignedBy;
        a.assignedAt = Instant.now();
        a.notes = notes;
        return a;
    }

    public void close() {
        this.returnedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public User getUser() {
        return user;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public String getNotes() {
        return notes;
    }
}
