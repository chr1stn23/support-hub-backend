package com.chrisdev.supporthub.user.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @BatchSize(size = 50)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Column(name = "security_stamp", nullable = false)
    private UUID securityStamp;

    @Column(name = "last_login_at", insertable = false, updatable = false)
    private Instant lastLoginAt;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName) {
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.securityStamp = UUID.randomUUID();
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public List<String> roleNames() {
        return roles.stream().map(r -> r.getName().name()).sorted().toList();
    }

    public boolean hasRole(RoleName role) {
        return roles.stream().anyMatch(r -> r.getName() == role);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isActive() {
        return active;
    }

    public void changeFullName(String fullName) {
        this.fullName = fullName.trim();
    }

    public void changeEmail(String email) {
        this.email = email.trim().toLowerCase(Locale.ROOT);
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void replaceRoles(Set<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
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

    public void rotateSecurityStamp() {
        this.securityStamp = UUID.randomUUID();
    }

    public UUID getSecurityStamp() {
        return securityStamp;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
