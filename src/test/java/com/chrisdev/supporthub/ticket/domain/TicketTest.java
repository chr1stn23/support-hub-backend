package com.chrisdev.supporthub.ticket.domain;

import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.support.TestEntities;
import com.chrisdev.supporthub.user.domain.User;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TicketTest {

    private final User agent = TestEntities.user("Carlos Agente");

    private static void assertRejected(ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(expected));
    }

    private Ticket openTicket() {
        return Ticket.open("  Laptop no enciende  ", "  Descripción larga de prueba  ",
                TestEntities.category(), TicketPriority.HIGH, TestEntities.user("Lucía Usuaria"), null);
    }

    private Ticket inProgressTicket() {
        Ticket ticket = openTicket();
        ticket.assignTo(agent);
        return ticket;
    }

    @Test
    void aNewTicketIsOpenUnassignedAndTrimmed() {
        Ticket ticket = openTicket();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getAgent()).isNull();
        assertThat(ticket.getTitle()).isEqualTo("Laptop no enciende");
        assertThat(ticket.isLocked()).isFalse();
    }

    @Test
    void assigningAnOpenTicketStartsTheWork() {
        Ticket ticket = openTicket();
        boolean started = ticket.assignTo(agent);

        assertThat(started).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getAgent()).isSameAs(agent);
    }

    @Test
    void reassigningAnInProgressTicketKeepsItsStatus() {
        Ticket ticket = inProgressTicket();
        boolean started = ticket.assignTo(TestEntities.user("Elena Agente"));

        assertThat(started).isFalse();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void invalidTransitionsAreRejected() {
        assertRejected(() -> openTicket().changeStatus(TicketStatus.CLOSED), ErrorCode.INVALID_STATUS_TRANSITION);
        assertRejected(() -> inProgressTicket().changeStatus(TicketStatus.CLOSED), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void inProgressRequiresAnAssignedAgent() {
        // OPEN → IN_PROGRESS es una transición válida, pero sin agente no se permite
        assertRejected(() -> openTicket().changeStatus(TicketStatus.IN_PROGRESS), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void resolvingStampsResolvedAtAndReopeningClearsIt() {
        Ticket ticket = inProgressTicket();

        ticket.changeStatus(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();

        ticket.changeStatus(TicketStatus.IN_PROGRESS);   // reapertura
        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void closingStampsClosedAtAndLocksTheTicket() {
        Ticket ticket = inProgressTicket();
        ticket.changeStatus(TicketStatus.RESOLVED);
        ticket.changeStatus(TicketStatus.CLOSED);

        assertThat(ticket.getClosedAt()).isNotNull();
        assertThat(ticket.isLocked()).isTrue();
        assertThat(ticket.isAssignable()).isFalse();
        assertRejected(() -> ticket.changeStatus(TicketStatus.IN_PROGRESS), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void aCancelledTicketIsFinal() {
        Ticket ticket = openTicket();
        ticket.changeStatus(TicketStatus.CANCELLED);

        assertThat(ticket.isLocked()).isTrue();
        assertThat(ticket.isAssignable()).isFalse();
    }
}
