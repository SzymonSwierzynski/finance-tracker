-- Phase 4: the remembered CSV column mapping for an account, so re-importing the same bank export
-- needs no re-mapping. One profile per (user, account).

CREATE TABLE import_profiles (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id          BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    delimiter           TEXT,
    encoding            TEXT,
    has_header          BOOLEAN     NOT NULL DEFAULT TRUE,
    date_index          INTEGER     NOT NULL,
    date_format         TEXT        NOT NULL DEFAULT 'auto',
    description_index    INTEGER     NOT NULL,
    amount_mode         TEXT        NOT NULL,
    amount_index        INTEGER     NOT NULL DEFAULT -1,
    expense_is_negative BOOLEAN     NOT NULL DEFAULT TRUE,
    debit_index         INTEGER     NOT NULL DEFAULT -1,
    credit_index        INTEGER     NOT NULL DEFAULT -1,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT import_profiles_amount_mode_chk CHECK (amount_mode IN ('signed', 'debitCredit')),
    CONSTRAINT uq_import_profiles_account UNIQUE (user_id, account_id)
);

CREATE INDEX idx_import_profiles_user ON import_profiles (user_id);
