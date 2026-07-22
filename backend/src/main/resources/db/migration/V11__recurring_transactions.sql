-- Phase 6: recurring transaction templates (rent, salary). A template materializes into real
-- transactions on its schedule; each materialized row links back via transactions.recurring_id.

CREATE TABLE recurring_transactions (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id     BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    category_id    BIGINT      REFERENCES categories (id) ON DELETE SET NULL,
    amount_minor   BIGINT      NOT NULL,
    type           TEXT        NOT NULL,
    currency       TEXT        NOT NULL,
    description    TEXT        NOT NULL DEFAULT '',
    note           TEXT        NOT NULL DEFAULT '',
    frequency      TEXT        NOT NULL,
    interval_count INTEGER     NOT NULL DEFAULT 1,
    start_date     DATE        NOT NULL,
    end_date       DATE,
    next_run_date  DATE        NOT NULL,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT recurring_type_chk CHECK (type IN ('expense', 'income')),
    CONSTRAINT recurring_freq_chk CHECK (frequency IN ('daily', 'weekly', 'monthly', 'yearly')),
    CONSTRAINT recurring_amount_chk CHECK (amount_minor > 0),
    CONSTRAINT recurring_interval_chk CHECK (interval_count > 0)
);

CREATE INDEX idx_recurring_user ON recurring_transactions (user_id);
CREATE INDEX idx_recurring_due ON recurring_transactions (active, next_run_date);

-- Link materialized transactions back to their template; nulled if the template is deleted.
ALTER TABLE transactions
    ADD COLUMN recurring_id BIGINT REFERENCES recurring_transactions (id) ON DELETE SET NULL;
