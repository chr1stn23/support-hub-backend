CREATE SEQUENCE ticket_code_seq START 1;

CREATE TABLE tickets
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    ticket_code  VARCHAR(20)  NOT NULL UNIQUE
                                       DEFAULT ('TCK-' || lpad(nextval('ticket_code_seq')::text, 6, '0')),
    title        VARCHAR(200) NOT NULL,
    description  TEXT         NOT NULL,
    category_id  UUID         NOT NULL REFERENCES categories (id),
    priority     VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    requester_id UUID         NOT NULL REFERENCES users (id),
    agent_id     UUID REFERENCES users (id),
    asset_id     UUID REFERENCES assets (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_tickets_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_tickets_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED', 'CANCELLED'))
);

CREATE INDEX idx_tickets_status ON tickets (status);
CREATE INDEX idx_tickets_priority ON tickets (priority);
CREATE INDEX idx_tickets_requester ON tickets (requester_id);
CREATE INDEX idx_tickets_agent ON tickets (agent_id);
CREATE INDEX idx_tickets_asset ON tickets (asset_id);
CREATE INDEX idx_tickets_category ON tickets (category_id);
CREATE INDEX idx_tickets_created_at ON tickets (created_at DESC);

-- Cola de trabajo de los agentes: tickets sin asignar
CREATE INDEX idx_tickets_unassigned ON tickets (created_at)
    WHERE agent_id IS NULL AND status = 'OPEN';

CREATE TABLE ticket_comments
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    ticket_id  UUID        NOT NULL REFERENCES tickets (id),
    author_id  UUID        NOT NULL REFERENCES users (id),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_comments_ticket ON ticket_comments (ticket_id, created_at);

CREATE TABLE ticket_history
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    ticket_id  UUID        NOT NULL REFERENCES tickets (id),
    actor_id   UUID        NOT NULL REFERENCES users (id),
    event_type VARCHAR(40) NOT NULL,
    old_value  VARCHAR(255),
    new_value  VARCHAR(255),
    note       VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_history_ticket ON ticket_history (ticket_id, created_at);

CREATE TABLE ticket_attachments
(
    id                UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    ticket_id         UUID         NOT NULL REFERENCES tickets (id),
    uploaded_by       UUID         NOT NULL REFERENCES users (id),
    original_filename VARCHAR(255) NOT NULL,
    storage_key       VARCHAR(255) NOT NULL UNIQUE,
    content_type      VARCHAR(120) NOT NULL,
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes > 0),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_attachments_ticket ON ticket_attachments (ticket_id);