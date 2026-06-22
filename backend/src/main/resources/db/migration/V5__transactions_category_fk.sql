-- Phase 3: now that categories exist, wire the deferred transactions.category_id FK.
-- ON DELETE SET NULL: deleting a category uncategorizes its transactions (CLAUDE.md §6).

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_category
        FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL;
