-- Phase 3.5: record WHETHER default categories were seeded, instead of inferring it from
-- "this user has no categories". The old inference re-seeded the 22 defaults on the next login
-- for anyone who had deliberately deleted them all.

ALTER TABLE settings
    ADD COLUMN categories_seeded_at TIMESTAMPTZ;

-- Backfill: a user who already owns categories was seeded under the old rule. Marking them here
-- is what stops the seeder from ever running against them again. Users with no categories are
-- left NULL so they still get their defaults on next login (the original backfill intent).
UPDATE settings s
SET categories_seeded_at = now()
WHERE EXISTS (SELECT 1 FROM categories c WHERE c.user_id = s.user_id);
