CREATE SEQUENCE asset_tag_seq START 1;

CREATE TABLE assets
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    asset_tag     VARCHAR(20) NOT NULL UNIQUE
                                       DEFAULT ('AST-' || lpad(nextval('asset_tag_seq')::text, 6, '0')),
    asset_type_id UUID        NOT NULL REFERENCES asset_types (id),
    brand         VARCHAR(80),
    model         VARCHAR(120),
    serial_number VARCHAR(120),
    status        VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    location      VARCHAR(120),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_assets_status
        CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'MAINTENANCE', 'RETIRED'))
);

-- Serial único, pero permitiendo activos sin serial
CREATE UNIQUE INDEX uq_assets_serial ON assets (serial_number)
    WHERE serial_number IS NOT NULL;

CREATE INDEX idx_assets_status ON assets (status);
CREATE INDEX idx_assets_type ON assets (asset_type_id);

CREATE TABLE asset_assignments
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    asset_id    UUID        NOT NULL REFERENCES assets (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    assigned_by UUID        NOT NULL REFERENCES users (id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    returned_at TIMESTAMPTZ,
    notes       VARCHAR(500),
    CONSTRAINT chk_assignment_dates
        CHECK (returned_at IS NULL OR returned_at >= assigned_at)
);

-- LA REGLA CLAVE: máximo UNA asignación vigente por activo
CREATE UNIQUE INDEX uq_asset_assignments_active
    ON asset_assignments (asset_id)
    WHERE returned_at IS NULL;

CREATE INDEX idx_asset_assignments_asset ON asset_assignments (asset_id);
CREATE INDEX idx_asset_assignments_user ON asset_assignments (user_id);
