-- Backlog B: idempotency keys for POST /transactions and POST /imports/commit. A client that sends
-- the same Idempotency-Key twice gets the first response replayed instead of a duplicate. Scoped per
-- (user, endpoint); the fingerprint guards against reusing a key for a different request.

CREATE TABLE idempotency_keys (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope               TEXT        NOT NULL,
    idempotency_key     TEXT        NOT NULL,
    request_fingerprint TEXT        NOT NULL,
    response_body       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_keys_uq UNIQUE (user_id, scope, idempotency_key)
);
CREATE INDEX idx_idempotency_keys_created ON idempotency_keys (created_at);
