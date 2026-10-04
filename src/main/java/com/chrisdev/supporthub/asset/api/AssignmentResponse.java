package com.chrisdev.supporthub.asset.api;

import com.chrisdev.supporthub.asset.domain.AssetAssignment;
import com.chrisdev.supporthub.user.api.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        UserSummary user,
        UserSummary assignedBy,
        Instant assignedAt,
        Instant returnedAt,
        String notes
) {

    public static AssignmentResponse from(AssetAssignment a) {
        return new AssignmentResponse(a.getId(), UserSummary.from(a.getUser()),
                UserSummary.from(a.getAssignedBy()), a.getAssignedAt(), a.getReturnedAt(), a.getNotes());
    }
}