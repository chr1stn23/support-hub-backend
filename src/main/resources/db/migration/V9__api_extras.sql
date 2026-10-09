-- Orden real por prioridad (CRITICAL antes que LOW); se recalcula sola al cambiar la prioridad
ALTER TABLE tickets
    ADD COLUMN priority_rank INTEGER GENERATED ALWAYS AS (
        CASE priority
            WHEN 'LOW' THEN 1
            WHEN 'MEDIUM' THEN 2
            WHEN 'HIGH' THEN 3
            WHEN 'CRITICAL' THEN 4
            END
        ) STORED;

CREATE INDEX idx_tickets_priority_rank ON tickets (priority_rank DESC, created_at DESC);

-- Las notificaciones de equipos también necesitan un enlace
ALTER TABLE notifications
    ADD COLUMN asset_id UUID REFERENCES assets (id);