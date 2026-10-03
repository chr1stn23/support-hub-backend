package com.chrisdev.supporthub.user.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleName name;

    protected Role() {
    }

    public UUID getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }
}
