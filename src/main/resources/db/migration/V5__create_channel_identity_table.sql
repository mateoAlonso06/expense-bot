-- A user's account on a messaging channel (Telegram, WhatsApp), identified by the id the channel gives the sender.
-- (channel, external_id) is unique: one Telegram account belongs to one user.
-- (user_id, channel) is unique: a user links at most one account per channel.
CREATE TABLE channel_identity (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES user_profile (id),
    channel     VARCHAR(20)  NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (channel, external_id),
    UNIQUE (user_id, channel)
);
