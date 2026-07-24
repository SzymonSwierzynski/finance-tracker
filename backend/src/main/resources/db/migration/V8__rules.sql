-- Phase 4: auto-categorization rules. A rule assigns its category to any transaction whose
-- description contains its (case-insensitive) pattern; higher priority wins, ties broken by pattern.
-- Deleting the referenced category deletes the rule — a rule pointing at a gone category cannot fire.

CREATE TABLE rules (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    pattern     TEXT        NOT NULL,
    category_id BIGINT      NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    priority    INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT rules_pattern_not_blank CHECK (length(btrim(pattern)) > 0)
);

CREATE INDEX idx_rules_user ON rules (user_id);
CREATE INDEX idx_rules_category ON rules (category_id);
