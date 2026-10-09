package com.chrisdev.supporthub.ticket.infrastructure;

import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.ticket.domain.TicketPriority;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    /**
     * Punto de partida neutro para ir encadenando con .and(...).
     */
    public static Specification<Ticket> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Ticket> requesterIs(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("requester").get("id"), userId);
    }

    public static Specification<Ticket> statusIn(Collection<TicketStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Ticket> priorityIn(Collection<TicketPriority> priorities) {
        return (root, query, cb) -> root.get("priority").in(priorities);
    }

    public static Specification<Ticket> inCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Ticket> assignedTo(UUID agentId) {
        return (root, query, cb) -> cb.equal(root.get("agent").get("id"), agentId);
    }

    public static Specification<Ticket> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get("agent"));
    }

    public static Specification<Ticket> forAsset(UUID assetId) {
        return (root, query, cb) -> cb.equal(root.get("asset").get("id"), assetId);
    }

    public static Specification<Ticket> createdFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from);
    }

    public static Specification<Ticket> createdBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.<Instant>get("createdAt"), to);
    }

    /**
     * Busca el texto en título, descripción o código del ticket (sin distinguir mayúsculas).
     */
    public static Specification<Ticket> matches(String text) {
        return (root, query, cb) -> {
            String pattern = "%" + escapeLike(text.toLowerCase()) + "%";
            return cb.or(
                    cb.like(cb.lower(root.<String>get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("description")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("ticketCode")), pattern, '\\'));
        };
    }

    // Si el usuario escribe "50%" o "a_b", esos símbolos no deben actuar como comodines SQL
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
