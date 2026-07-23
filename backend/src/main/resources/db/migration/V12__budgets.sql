-- Phase 7: per-category monthly budgets. The limit is held in the user's reporting (base) currency
-- minor units, so budget progress compares directly against base-currency spend (which is how every
-- report aggregates). At most one budget per category, enforced by a unique constraint.

CREATE TABLE budgets (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id  BIGINT      NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    amount_minor BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT budgets_amount_chk CHECK (amount_minor > 0),
    CONSTRAINT budgets_category_uq UNIQUE (user_id, category_id)
);

CREATE INDEX idx_budgets_user ON budgets (user_id);
