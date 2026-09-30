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

INSERT INTO category (id, name, system) VALUES
    (gen_random_uuid(), 'Food', FALSE),
    (gen_random_uuid(), 'Housing', FALSE),
    (gen_random_uuid(), 'Transport', FALSE),
    (gen_random_uuid(), 'Health', FALSE),
    (gen_random_uuid(), 'Leisure', FALSE),
    (gen_random_uuid(), 'Services', FALSE),
    ('00000000-0000-0000-0000-000000000001', 'Other', TRUE);
