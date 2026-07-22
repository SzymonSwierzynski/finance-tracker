-- Phase 3.5: the user-managed FX rate table (CLAUDE.md §7). Rates are anchored to the user's
-- reporting currency, so any pair cross-converts through base. This is the table RateResolver
-- consults when a transaction arrives in a currency other than base without an explicit rate —
-- without it, every foreign-currency row (including every imported one in Phase 4) has to carry a
-- hand-supplied rate.
--
-- rate_to_base is NUMERIC: rates are the ONE non-integer quantity in the model. Money stays BIGINT.
CREATE TABLE fx_rates (
    id              BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    currency        TEXT           NOT NULL,
    rate_to_base    NUMERIC(20, 8) NOT NULL,
    -- The reporting currency this rate was anchored to when it was saved. Kept per row because
    -- changing the reporting currency silently invalidates every stored rate: 4.30 was "PLN per
    -- EUR", and it is not "USD per EUR". Storing the anchor lets the resolver refuse a stale rate
    -- instead of quietly booking a wrong base amount onto a transaction, forever.
    base_currency   TEXT           NOT NULL,
    -- Provenance for an optional provider sync; NULL means the user typed it.
    source          TEXT,
    last_fetched_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fx_rates_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT fx_rates_base_currency_chk CHECK (base_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT fx_rates_positive_chk CHECK (rate_to_base > 0),
    -- A rate from a currency to itself is 1 by definition and is never stored.
    CONSTRAINT fx_rates_not_base_chk CHECK (currency <> base_currency)
);

-- One rate per currency per user; the upsert in FxRateService relies on this.
CREATE UNIQUE INDEX uq_fx_rates_user_currency ON fx_rates (user_id, currency);
