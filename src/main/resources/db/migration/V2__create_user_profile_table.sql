-- Business-side profile of a Keycloak user. The id is the token's `sub`, so no id is generated here.
CREATE TABLE user_profile (
    id         UUID         PRIMARY KEY,
    email      VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
