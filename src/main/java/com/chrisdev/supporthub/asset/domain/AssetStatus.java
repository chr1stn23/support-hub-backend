package com.chrisdev.supporthub.asset.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AssetStatus {
    AVAILABLE, ASSIGNED, MAINTENANCE, RETIRED;

    public Set<AssetStatus> allowedTransitions() {
        return switch (this) {
            case AVAILABLE -> EnumSet.of(ASSIGNED, MAINTENANCE, RETIRED);
            case ASSIGNED -> EnumSet.of(AVAILABLE);
            case MAINTENANCE -> EnumSet.of(AVAILABLE, RETIRED);
            case RETIRED -> EnumSet.noneOf(AssetStatus.class);
        };
    }

    public boolean canTransitionTo(AssetStatus target) {
        return allowedTransitions().contains(target);
    }

    public String label() {
        return switch (this) {
            case AVAILABLE -> "Disponible";
            case ASSIGNED -> "Asignado";
            case MAINTENANCE -> "En mantenimiento";
            case RETIRED -> "De baja";
        };
    }
}
