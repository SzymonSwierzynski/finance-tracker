-- Phase 1 baseline: identity + settings + refresh tokens.
-- Forward-only and immutable once merged. Later phases add their own tables.

-- Case-insensitive unique email.
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         CITEXT      NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    display_name  TEXT,
    status        TEXT        NOT NULL DEFAULT 'active',
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT users_status_chk CHECK (status IN ('active', 'disabled'))
);

-- 1:1 with users; reporting currency drives base-currency rollups (default PLN).
CREATE TABLE settings (
    user_id            BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    reporting_currency TEXT        NOT NULL DEFAULT 'PLN',
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT settings_currency_chk CHECK (reporting_currency ~ '^[A-Z]{3}$')
);

-- Refresh tokens are stored hashed (never the raw value), rotating and revocable.
CREATE TABLE refresh_tokens (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
