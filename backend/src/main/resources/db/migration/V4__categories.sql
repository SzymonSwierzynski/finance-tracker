-- Phase 3: categories. Two levels only (a category with a parent may not itself be a parent) —
-- enforced in the service; the DB guarantees ownership, kind/color shape, and name uniqueness.
-- Deleting a parent cascades to its subcategories (ON DELETE CASCADE on parent_id); each removed
-- category then uncategorizes its transactions via the FK added in V5 (ON DELETE SET NULL).

CREATE TABLE categories (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    kind       TEXT        NOT NULL,
    parent_id  BIGINT      REFERENCES categories (id) ON DELETE CASCADE,
    color      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT categories_kind_chk CHECK (kind IN ('expense', 'income')),
    CONSTRAINT categories_color_chk CHECK (color ~ '^#[0-9a-fA-F]{6}$')
);

CREATE INDEX idx_categories_user ON categories (user_id);
CREATE INDEX idx_categories_parent ON categories (parent_id);

-- Unique (userId, parentId, name) — split so NULL parent_id (top-level) is still de-duplicated,
-- which a plain composite UNIQUE would not do (NULLs are distinct in SQL).
CREATE UNIQUE INDEX uq_categories_top ON categories (user_id, name) WHERE parent_id IS NULL;
CREATE UNIQUE INDEX uq_categories_sub ON categories (user_id, parent_id, name) WHERE parent_id IS NOT NULL;
