CREATE TABLE notifications
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id),
    type       VARCHAR(40)  NOT NULL,
    message    VARCHAR(500) NOT NULL,
    ticket_id  UUID REFERENCES tickets (id),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);

-- Para contar rápido las no leídas
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE audit_logs
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    actor_id    UUID REFERENCES users (id), -- NULL = acción del sistema
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id   UUID,
    result      VARCHAR(10) NOT NULL DEFAULT 'SUCCESS',
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_actor ON audit_logs (actor_id, created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at DESC);