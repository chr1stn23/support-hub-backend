package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.ticket.domain.TicketStatus;

import java.util.List;

/**
 * Qué puede hacer QUIEN consulta sobre este ticket. Calculado con la misma política que valida las operaciones.
 */
public record TicketActions(
        boolean editContent,      // título y descripción
        boolean editTriage,       // prioridad, categoría y equipo
        boolean comment,
        boolean attach,
        boolean assignToSelf,     // tomarlo (o reasignárselo a sí mismo, si es admin)
        boolean assignToOthers,   // solo admin
        List<StatusOption> changeStatus
) {

    public record StatusOption(TicketStatus status, boolean noteRequired) {
    }

}
