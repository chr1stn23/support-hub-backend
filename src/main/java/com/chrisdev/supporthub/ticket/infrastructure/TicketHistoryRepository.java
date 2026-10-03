package com.chrisdev.supporthub.ticket.infrastructure;

import com.chrisdev.supporthub.ticket.domain.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, UUID> {
}
