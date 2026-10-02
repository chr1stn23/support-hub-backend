CREATE TABLE roles
(
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE users
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0
);

-- Email único sin distinguir mayúsculas
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE user_roles
(
    user_id UUID NOT NULL REFERENCES users (id),
    role_id UUID NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);

INSERT INTO roles (name)
VALUES ('ADMIN'),
       ('AGENT'),
       ('USER'),
       ('ASSET_MANAGER');

