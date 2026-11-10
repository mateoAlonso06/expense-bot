-- Idempotency log for incoming messages. event_id is the channel-wide delivery id (Telegram: update_id),
-- not the platform's message_id, which is only unique per chat. The primary key is what makes a
-- concurrent duplicate fail: only one INSERT for the same (channel, event_id) can succeed.
CREATE TABLE processed_message (
    channel     VARCHAR(20)  NOT NULL,
    event_id    VARCHAR(255) NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (channel, event_id)
);
