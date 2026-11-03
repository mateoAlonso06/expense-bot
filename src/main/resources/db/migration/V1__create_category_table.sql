CREATE TABLE category (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    system     BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id    UUID,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Global catalog (user_id IS NULL): names are unique, case-insensitive.
CREATE UNIQUE INDEX uq_category_global_name
    ON category (lower(name))
    WHERE user_id IS NULL;

-- Names are user-facing data (Spanish). The system category is looked up by its fixed id, never by name.
INSERT INTO category (id, name, system) VALUES
    (gen_random_uuid(), 'Alimentación', FALSE),
    (gen_random_uuid(), 'Vivienda', FALSE),
    (gen_random_uuid(), 'Transporte', FALSE),
    (gen_random_uuid(), 'Salud', FALSE),
    (gen_random_uuid(), 'Ocio', FALSE),
    (gen_random_uuid(), 'Servicios', FALSE),
    ('00000000-0000-0000-0000-000000000001', 'Otros', TRUE);
