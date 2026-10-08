package com.chrisdev.supporthub.ticket.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.chrisdev.supporthub.ticket.domain.TicketStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

public class TicketStatusTest {

    private static final Map<TicketStatus, Set<TicketStatus>> EXPECTED = Map.of(
            OPEN, EnumSet.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS, EnumSet.of(WAITING, RESOLVED, CANCELLED),
            WAITING, EnumSet.of(IN_PROGRESS, CANCELLED),
            RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS),
            CLOSED, EnumSet.noneOf(TicketStatus.class),
            CANCELLED, EnumSet.noneOf(TicketStatus.class));

    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    void allowedTransitionsMatchTheSpecification(TicketStatus from) {
        assertThat(from.allowedTransitions()).containsExactlyInAnyOrderElementsOf(EXPECTED.get(from));
    }

    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    void canTransitionToAgreesWithTheTable(TicketStatus from) {
        for (TicketStatus to : TicketStatus.values()) {
            assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to)
                    .isEqualTo(EXPECTED.get(from).contains(to));
        }
    }

    @Test
    void nobodyCanSkipTheResolutionStep() {
        assertThat(OPEN.canTransitionTo(CLOSED)).isFalse();
        assertThat(IN_PROGRESS.canTransitionTo(CLOSED)).isFalse();
        assertThat(WAITING.canTransitionTo(RESOLVED)).isFalse();
    }
}
