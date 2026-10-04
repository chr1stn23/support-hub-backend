package com.chrisdev.supporthub.ticket.domain;

import java.util.EnumSet;
import java.util.Set;

public enum TicketStatus {
    OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED, CANCELLED;

    public Set<TicketStatus> allowedTransitions() {
        return switch (this) {
            case OPEN, WAITING -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(WAITING, RESOLVED, CANCELLED);
            case RESOLVED -> EnumSet.of(CLOSED, IN_PROGRESS);   // IN_PROGRESS = reapertura
            case CLOSED, CANCELLED -> EnumSet.noneOf(TicketStatus.class);
        };
    }

    public boolean canTransitionTo(TicketStatus target) {
        return allowedTransitions().contains(target);
    }
}