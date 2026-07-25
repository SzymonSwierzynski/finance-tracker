# Architecture

## Overview

```
frontend/ (Vite 8 · React 19 · TS strict · Tailwind v4 · TanStack Query · React Router 8)
    ↕ HTTPS / JSON  —  /api/v1/*
backend/  (Spring Boot 3.5 · Java 21 · Spring Data JPA · Spring Security JWT · Flyway)
    ↕ JDBC
PostgreSQL 16  (money = BIGINT minor units)
```

- Base API path: `/api/v1`. OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`.
- Java 21 (the Gradle toolchain is pinned to 21).
- In production the SPA and API are served **same-origin** behind nginx so the refresh cookie stays
  first-party — see [deployment.md](../guides/deployment.md).

## Layering (strict)

**Controller → Service → Repository.** Entities never leave the service layer; DTOs (Java records
with Bean Validation) cross the boundary. Repositories expose only user-scoped finders.

On the frontend: **components → hooks (TanStack Query) → feature `api.ts` → `src/api/client.ts`.**
Never `fetch` from a component.

## Domain rules (non-negotiable)

1. **Money is integer minor units** (`long` / `BIGINT`). `19.99 PLN` is `1999`. Convert to decimal
   only in display formatters (`MoneyUtil` on the backend, `lib/money.ts` on the frontend).
2. **FX rates are locked at entry.** A transaction stores `rateToBase` and a derived
   `baseMinor = round(amountMinor * rateToBase)`. Aggregations use the locked base value, never live
   FX. A transaction in the reporting currency has `rateToBase = 1`; a foreign-currency transaction
   needs an explicit rate or a resolvable `fx_rates` row — otherwise the request is rejected (422).
3. **Every user-owned row is scoped by `user_id`.** Cross-user access returns **404**, not 403.
4. **Optimistic locking** via a `version` column; stale writes return **409**. Services `saveAndFlush`
   on update so the incremented version is visible in the response.
5. **Errors are RFC 9457 `application/problem+json`** from a single `GlobalExceptionHandler`
   (validation/domain → 422, missing/cross-user → 404, optimistic-lock → 409, unexpected → 500).
6. **Categories are two levels.** Deleting a category sets transactions' `category_id` to null
   (uncategorize); deleting a parent cascades to subcategories.
7. **Transfers** require a `counterAccountId` and must have a null `categoryId`.
8. **FX rows are anchored** to the base currency they were saved against. Change the reporting
   currency and stored rates go stale; the resolver then refuses (422) rather than book a wrong,
   frozen base amount.
9. **Dedupe hash** is FNV-1a 32-bit over `[date, amountMinor, currency, accountId, description]`.
   Manual entry and CSV import use the same inputs.

## Authentication

| Token         | Transport                                   | Storage |
|---------------|---------------------------------------------|---------|
| Access token  | JSON body (`TokenResponse.accessToken`)     | In-memory only on the frontend — never `localStorage` |
| Refresh token | HttpOnly cookie (`refreshToken`, path `/api/v1/auth`) | Browser cookie; SHA-256 hash stored server-side |

- JWT is **RS256** (an ephemeral key pair is generated in dev if env keys are absent).
- `client.ts` attaches `Authorization: Bearer …` and, on a 401, retries once via
  `POST /api/v1/auth/refresh` with `credentials: 'include'`.
- **Refresh tokens are single-use.** Replaying a rotated token revokes every session for that user
  (reuse detection); revoked rows are retained until expiry so the check can work.
- **Default category seeding** runs on register and login, idempotent, tracked by
  `settings.categories_seeded_at`. New users get 26 default categories.

## Backend package map

```
com.financetracker/
├── auth/  settings/  account/  transaction/  category/  reporting/  fx/
├── rule/  importing/  recurring/  export/  budget/
├── common/{money, hash, web, error, observability, security}
└── config/  (Security, CORS, Web, OpenAPI, JWT keys, Scheduling, rate-limit props)
```

The breakdown roll-up lives in `ReportingService.breakdown()`: SQL groups by `category_id`, Java
rolls subcategory spend up to the parent, direct parent spend becomes a synthetic `"Parent (direct)"`
child, and a null category becomes `"Uncategorized"`. The Trends period-comparison movers
(`ReportingService.trendComparison()`) reuse the same parent roll-up.

## Database schema (Flyway, forward-only)

| Migration | Contents |
|-----------|----------|
| `V1__baseline` | `users` (email citext), `settings`, `refresh_tokens` |
| `V2__accounts` | `accounts` |
| `V3__transactions` | `transactions` |
| `V4__categories` | `categories` (two-level, unique name per parent) |
| `V5__transactions_category_fk` | FK `transactions.category_id → categories.id ON DELETE SET NULL` |
| `V6__settings_categories_seeded_at` | seeding marker + backfill |
| `V7__fx_rates` | `fx_rates` (anchored `base_currency`) |
| `V8__rules` | `rules` (pattern, category, priority) |
| `V9__import_batches` | `import_batches` + `transactions.import_batch_id` (ON DELETE CASCADE) |
| `V10__import_profiles` | per-account remembered CSV mapping |
| `V11__recurring_transactions` | `recurring_transactions` + `transactions.recurring_id` |
| `V12__budgets` | `budgets` (per user, unique category, base minor units) |

**Next migration: `V13`.** Never edit a shipped migration.

> **JDBC note:** the datasource uses `stringtype=unspecified` so citext email comparisons work. This
> caused Postgres `42P18` errors with nullable JPQL params — solved with JPA Specifications / the
> Criteria API, not string concatenation.
