-- One-time codes that let a web user link a channel identity (Telegram, WhatsApp) to their account.
-- Only the SHA-256 hash of the code is stored. A code is consumed by setting used_at.
CREATE TABLE link_code (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES user_profile (id),
    code_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
