-- Revocación inmediata de tokens: si el sello cambia, los JWT emitidos dejan de valer
ALTER TABLE users
    ADD COLUMN security_stamp UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE users
    ADD COLUMN last_login_at TIMESTAMPTZ;

-- Refresh tokens: solo se guarda el hash
CREATE TABLE refresh_tokens
(
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id),
    family_id         UUID        NOT NULL,
    token_hash        VARCHAR(64) NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NOT NULL,
    family_expires_at TIMESTAMPTZ NOT NULL,
    used_at           TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens (family_expires_at);