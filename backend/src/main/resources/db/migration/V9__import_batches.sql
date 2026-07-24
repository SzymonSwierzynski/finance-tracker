-- Phase 4: an import batch groups the transactions created by one CSV commit so the whole import is
-- undoable. Undo deletes the batch row; its transactions go with it via the FK below.

CREATE TABLE import_batches (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    file_name  TEXT        NOT NULL,
    row_count  INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_import_batches_user ON import_batches (user_id);
CREATE INDEX idx_import_batches_account ON import_batches (user_id, account_id);

-- transactions.import_batch_id was a placeholder column since V3; wire it up now. ON DELETE CASCADE
-- makes "undo a batch" a single delete of the batch row.
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_import_batch
    FOREIGN KEY (import_batch_id) REFERENCES import_batches (id) ON DELETE CASCADE;
