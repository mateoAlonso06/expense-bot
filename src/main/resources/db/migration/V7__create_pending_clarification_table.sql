-- A question the bot asked and is waiting for (#11): the amount of an expense, or which of several amounts it was.
-- At most one per conversation, so the key is (user_id, channel): a user links at most one account per channel.
-- Asking again replaces the row. amount_candidates is empty when the amount is missing, two or more when ambiguous.
-- description and category_name are the model's reading, kept as TEXT: they are checked when the expense is stored.
CREATE TABLE pending_clarification (
    user_id           UUID             NOT NULL REFERENCES user_profile (id),
    channel           VARCHAR(20)      NOT NULL,
    original_message  TEXT             NOT NULL,
    description       TEXT,
    category_name     TEXT,
    amount_candidates NUMERIC(19, 2)[] NOT NULL,
    expires_at        TIMESTAMPTZ      NOT NULL,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, channel)
);
