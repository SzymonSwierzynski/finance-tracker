-- Phase 2: accounts. User-owned; deleting an account is blocked while it has
-- transactions (archive instead) — enforced by the FK from transactions.account_id
-- added in V3 (ON DELETE RESTRICT by default).

CREATE TABLE accounts (
    id                     BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                   TEXT        NOT NULL,
    type                   TEXT        NOT NULL,
    currency               TEXT        NOT NULL,
    -- Only meaningful when track_balance is true (kept NULL otherwise).
    starting_balance_minor BIGINT,
    track_balance          BOOLEAN     NOT NULL DEFAULT FALSE,
    archived               BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT accounts_type_chk CHECK (type IN ('checking', 'savings', 'cash', 'credit')),
    CONSTRAINT accounts_currency_chk CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);
