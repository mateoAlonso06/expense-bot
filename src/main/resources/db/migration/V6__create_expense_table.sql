-- An expense a user logged through a channel. Date, source channel and original message are immutable
-- (enforced by the application: there is no way to change them).
-- amount is exact (NUMERIC, never floating point) and strictly positive. description is optional: only the amount
-- is mandatory when the user writes "15000".
CREATE TABLE expense (
    id               UUID          PRIMARY KEY,
    user_id          UUID          NOT NULL REFERENCES user_profile (id),
    category_id      UUID          NOT NULL REFERENCES category (id),
    amount           NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency         CHAR(3)       NOT NULL,
    description      VARCHAR(255),
    spent_on         DATE          NOT NULL,
    source_channel   VARCHAR(20)   NOT NULL,
    original_message TEXT          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- The main query of the product: one user's expenses, newest first (history, filters, dashboards).
CREATE INDEX idx_expense_user_spent_on ON expense (user_id, spent_on DESC);
