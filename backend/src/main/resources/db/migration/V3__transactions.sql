-- Phase 2: transactions. Money is BIGINT minor units (native currency); rate_to_base
-- is the ONLY NUMERIC and is locked at entry time so historical reports never shift.
-- category_id / import_batch_id are nullable now; their FKs are added by the migrations
-- that create those tables (categories = Phase 3, import_batches = Phase 4) — forward-only.

CREATE TABLE transactions (
    id                 BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date               DATE          NOT NULL,
    amount_minor       BIGINT        NOT NULL,
    type               TEXT          NOT NULL,
    -- RESTRICT (default): an account with transactions cannot be deleted; archive it instead.
    account_id         BIGINT        NOT NULL REFERENCES accounts (id),
    counter_account_id BIGINT        REFERENCES accounts (id),
    category_id        BIGINT,
    currency           TEXT          NOT NULL,
    rate_to_base       NUMERIC(20, 8) NOT NULL,
    description        TEXT          NOT NULL DEFAULT '',
    note               TEXT          NOT NULL DEFAULT '',
    import_batch_id    BIGINT,
    dedupe_hash        TEXT          NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT transactions_type_chk CHECK (type IN ('expense', 'income', 'transfer')),
    CONSTRAINT transactions_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT transactions_amount_pos_chk CHECK (amount_minor > 0),
    CONSTRAINT transactions_rate_pos_chk CHECK (rate_to_base > 0),
    -- Transfers carry a counter account and never a category; non-transfers carry no counter account.
    CONSTRAINT transactions_transfer_chk CHECK (
        (type = 'transfer' AND counter_account_id IS NOT NULL AND category_id IS NULL)
        OR (type <> 'transfer' AND counter_account_id IS NULL)
    )
);

CREATE INDEX idx_transactions_user_date ON transactions (user_id, date);
CREATE INDEX idx_transactions_user_account_date ON transactions (user_id, account_id, date);
CREATE INDEX idx_transactions_user_category ON transactions (user_id, category_id);
CREATE INDEX idx_transactions_dedupe ON transactions (user_id, account_id, dedupe_hash);
