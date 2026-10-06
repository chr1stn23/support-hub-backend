--- Tickets resueltos en un período (y su tiempo de resolución)
CREATE INDEX idx_tickets_resolved_at ON tickets (resolved_at)
    WHERE resolved_at IS NOT NULL;

--- Actividad reciente: últimos eventos de todos los tickets
CREATE INDEX idx_ticket_history_created_at ON ticket_history (created_at DESC);