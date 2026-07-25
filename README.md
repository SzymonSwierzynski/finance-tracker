# Personal Finance Tracker

Multi-user personal finance app — **see where your money goes**: spending by category over time,
across accounts and currencies, with Polish-bank CSV import. A React + Tailwind single-page app over
a Spring Boot REST API on PostgreSQL.

> **Status — feature-complete.** Auth, accounts, two-level categories, multi-currency transactions
> with FX locked at entry, CSV import + rules engine, recurring templates, budgets, reporting
> (breakdown / trend / cashflow / period comparison), export/backup/restore, observability, dark
> mode, and one-command Docker deployment are all shipped and green in CI.

## Features

- **Accounts & transactions** — multiple accounts and currencies; income / expense / transfer;
  filter, search, and sort. FX rate is locked at entry so historical reports never drift.
- **Categories** — two-level, with 26 sensible defaults seeded per user.
- **Reporting** — period summary, category breakdown (with subcategory roll-up), trend and cashflow
  charts, and period-over-period comparison (MoM / YoY, plus an equal-length "vs previous period"
  comparison on the Trends tab).
- **CSV import** — Polish-bank statements: encoding/delimiter/date auto-detection, dedupe, and
  rules-based auto-categorization, with undoable batches and remembered per-account column mappings.
- **Budgets** — per-category monthly budgets with progress and over/under tracking.
- **Recurring** — templates that materialize on schedule (with a nightly sweep).
- **Export / backup / restore** — CSV export and a full additive, idempotent JSON backup/restore.
- **Multi-currency** — a user-maintained FX rate table anchored to the reporting currency.
- **Polish-first UI** — pl-PL primary with English, and a class-based dark mode.

## Stack

| Layer     | Technology |
|-----------|------------|
| Frontend  | Vite 8 · React 19 · TypeScript (strict) · Tailwind v4 · TanStack Query · React Router 8 · i18next |
| Backend   | Spring Boot 3.5 · Java 21 · Spring Data JPA · Spring Security (JWT RS256) · Flyway |
| Database  | PostgreSQL 16 — money stored as `BIGINT` minor units |

## Layout

```
backend/    Spring Boot REST API at /api/v1  (see docs/reference/architecture.md)
frontend/   React SPA                        (see frontend/README.md)
docs/       Architecture, development, deployment, and API reference
reference/  The original local-first Dexie prototype (domain logic was ported from here)
```

## Quick start (whole stack)

Requires Docker. The app runs same-origin behind nginx, so the refresh cookie stays first-party.

```bash
docker compose up --build
```

- App (SPA + API proxy): <http://localhost:5173>
- Register a user in the UI, or via the API below.

## Run locally for development

Three processes: Postgres, the backend, and the Vite dev server.

```bash
# 1. Postgres
docker compose up -d db

# 2. Backend — http://localhost:8080  (JDK 21 via the Gradle toolchain)
cd backend && ./gradlew bootRun

# 3. Frontend — http://localhost:5173  (proxies /api → :8080)
cd frontend && npm install && npm run dev
```

- Swagger UI: <http://localhost:8080/swagger-ui.html> · OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Health: <http://localhost:8080/actuator/health>

See **[docs/guides/development.md](docs/guides/development.md)** for the full dev loop, testing, and conventions.

## API smoke test

```bash
# Register (refresh token is an HttpOnly cookie; the access token is in the body)
curl -i -c cookies.txt -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@example.com","password":"password123"}'

# Create an account (money is integer minor units — 1000.00 PLN = 100000)
curl -s -X POST localhost:8080/api/v1/accounts -H 'Authorization: Bearer <ACCESS_TOKEN>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Checking","type":"checking","currency":"PLN","trackBalance":true,"startingBalanceMinor":100000}'

# See where the money goes (income / expense / net, in base currency)
curl -s "localhost:8080/api/v1/reports/summary?from=2024-06-01&to=2024-06-30" \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'
```

Full endpoint reference: **[docs/reference/api.md](docs/reference/api.md)**.

## Key invariants

- **Money is integer minor units** (`long` / `BIGINT`); divide by 100 only at the display edge.
- **Every query is user-scoped**; cross-user access returns `404` (proven by isolation tests).
- **FX rates are locked at entry**; aggregations use the stored base value, never live FX.
- **Errors are RFC 9457 `problem+json`**; validation failures are `422` with field details.
- **Migrations are Flyway, forward-only**; never edit a shipped migration.

More: **[docs/reference/architecture.md](docs/reference/architecture.md)**.

## Documentation

- [docs/reference/architecture.md](docs/reference/architecture.md) — stack, layering, domain rules, module map, schema
- [docs/guides/development.md](docs/guides/development.md) — local dev loop, testing, conventions, CI
- [docs/guides/deployment.md](docs/guides/deployment.md) — Docker / nginx same-origin deployment and configuration
- [docs/reference/api.md](docs/reference/api.md) — REST endpoint reference
- [frontend/README.md](frontend/README.md) — frontend stack, scripts, and structure
