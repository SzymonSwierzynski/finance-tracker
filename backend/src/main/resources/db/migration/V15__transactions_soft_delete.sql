-- Backlog C: soft-delete for transactions. deleted_at NULL = active; non-null = trashed (invisible to
-- every active read, restorable, purged after retention). Partial indexes keep the hot active path
-- lean and support the trash listing.
ALTER TABLE transactions ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_transactions_active ON transactions (user_id, date) WHERE deleted_at IS NULL;
CREATE INDEX idx_transactions_trash  ON transactions (user_id, deleted_at) WHERE deleted_at IS NOT NULL;
