CREATE TABLE categories
(
    id     UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name   VARCHAR(80) NOT NULL UNIQUE,
    active BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE asset_types
(
    id     UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name   VARCHAR(80) NOT NULL UNIQUE,
    active BOOLEAN     NOT NULL DEFAULT TRUE
);

INSERT INTO categories (name)
VALUES ('Hardware'),
       ('Software'),
       ('Network'),
       ('Access'),
       ('Other');

INSERT INTO asset_types (name)
VALUES ('Laptop'),
       ('Monitor'),
       ('Printer'),
       ('Mobile phone'),
       ('Router'),
       ('Other');