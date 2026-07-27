-- Backlog A: opt-in budget rollover. When true, a month's available amount is the fixed limit plus
-- the accumulated unspent balance from prior months (floored at zero — no carried debt). Existing
-- rows default to false, preserving strict month-to-month behavior.

ALTER TABLE budgets ADD COLUMN rollover BOOLEAN NOT NULL DEFAULT FALSE;
