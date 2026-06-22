Personal Finance Tracker — Production Specification
A multi-user personal finance application whose core purpose never changes: SEE WHERE MY MONEY GOES — spending patterns by category over time, across accounts and currencies. This document is the contract for the production rebuild: a React + Tailwind single-page frontend talking to a Spring Boot REST backend over PostgreSQL. It supersedes the local-first Dexie prototype, but every hard-won domain rule from that prototype is carried forward verbatim below — those rules are non-negotiable and were validated against real Polish bank data.
Read this whole file before writing code. When something here conflicts with a default convention, this file wins. When something is genuinely ambiguous, ask before guessing.
0. Prime directives (read first, never violate)

1. MONEY IS STORED AS INTEGERS in minor units (grosze / cents), never floats, on BOTH frontend and backend. `19.99 PLN` is `1999`. The DB column is `BIGINT`. The Java type is `long` (never `double`/`float`, never `BigDecimal` for storage — `BigDecimal` is only allowed transiently inside FX/rounding math and is converted to `long` before it touches the DB or a DTO). Division by 100 happens in exactly one place per layer: the display formatter.
2. All persistence goes through the service/repository layer. Controllers never touch repositories or entities directly; they speak DTOs. The frontend never builds SQL-ish queries — it calls typed API client functions in one `src/api/` module. This keeps storage swappable and security centralized.
3. Aggregations always use the base (reporting) currency value, computed from a rate locked at transaction-entry time. Never convert live for historical totals. (See §7.)
4. Every endpoint is scoped to the authenticated user. There is no query, anywhere, that can return another user's row. Ownership is enforced in the data layer, not just the controller. (See §5.)
5. Strict typing everywhere. TypeScript `strict`, no `any`. Java with nullability annotations; no raw types; no `Optional` fields on entities used as method params.
6. Do the build phases in order (§16) and stop after each for review. Don't skip ahead. Don't gold-plate a later phase while an earlier one is unproven.
1. Architecture overview

```
┌─────────────────────────────┐        HTTPS / JSON        ┌──────────────────────────────┐
│  Frontend (SPA)             │  ───────────────────────▶  │  Backend (Spring Boot)        │
│  React 19 + TypeScript      │                            │  REST API, /api/v1/*          │
│  Vite, Tailwind, TanStack    │  ◀───────────────────────  │  Spring Security (JWT)        │
│  Query, React Router         │        DTOs (JSON)         │  Service + Repository layers  │
└─────────────────────────────┘                            └───────────────┬───────────────┘
                                                                            │ JPA / Flyway
                                                                            ▼
                                                                  ┌──────────────────┐
                                                                  │  PostgreSQL      │
                                                                  │  money = BIGINT  │
                                                                  └──────────────────┘

```

* Two deployables: `frontend/` (static bundle served by nginx) and `backend/` (Spring Boot fat JAR). One repo, two top-level modules. A `docker-compose.yml` at the root brings up `db`, `backend`, `frontend` for local dev and demo.
* Stateless backend. No server-side HTTP session; auth state lives in JWTs. This allows horizontal scaling and keeps the API testable.
* API-first. The OpenAPI spec is the source of truth for the contract; the TS API client types are generated from it (see §4, §11).
Repository layout

```
finance-tracker/
├─ CLAUDE.md                     # this file
├─ docker-compose.yml            # db + backend + frontend for local/demo
├─ .github/workflows/            # CI pipelines (§13)
├─ backend/
│  ├─ build.gradle.kts           # Gradle (Kotlin DSL) — or Maven; pick one, §3
│  ├─ Dockerfile
│  └─ src/
│     ├─ main/java/com/financetracker/
│     │  ├─ config/              # security, CORS, jackson, openapi, web
│     │  ├─ auth/                # registration, login, JWT, refresh
│     │  ├─ account/             # controller, service, repo, entity, dto, mapper
│     │  ├─ category/
│     │  ├─ transaction/
│     │  ├─ rule/                # auto-categorization
│     │  ├─ importing/           # CSV import pipeline
│     │  ├─ reporting/           # summaries, breakdowns, trends (read models)
│     │  ├─ fx/                  # currencies + rates
│     │  ├─ settings/
│     │  ├─ common/              # money utils, error handling, base entities
│     │  └─ Application.java
│     ├─ main/resources/
│     │  ├─ application.yml       # + application-{dev,prod,test}.yml
│     │  └─ db/migration/         # Flyway V1__*.sql, V2__*.sql ...
│     └─ test/java/...            # mirrors main; unit + slice + integration
└─ frontend/
   ├─ package.json
   ├─ Dockerfile + nginx.conf
   ├─ index.html
   └─ src/
      ├─ api/                    # generated types + typed fetch client, one entry
      ├─ app/                    # router, providers, layout, error boundary
      ├─ features/               # accounts, transactions, breakdown, import, rules, settings
      │  └─ <feature>/{components,hooks,api.ts}
      ├─ components/             # shared UI primitives (Button, Money, Modal...)
      ├─ lib/                    # money, dates, csv-helpers, formatting
      ├─ hooks/                  # cross-feature hooks
      └─ types/                  # shared domain types (mirror DTOs)

```

2. Frontend stack & conventions

* Vite + React 19 + TypeScript (strict, no `any`).
* Tailwind CSS for all styling. No CSS-in-JS, no ad-hoc stylesheets beyond a single `index.css` with Tailwind layers and design tokens. Prefer composing utility classes; extract a component when a class string repeats 3+ times.
* TanStack Query for all server state (caching, refetch, mutations, optimistic updates, invalidation). Do NOT store server data in global state.
* React Router for routing; one route per top-level view.
* React Hook Form + Zod for forms and client-side validation. Zod schemas mirror backend validation and are the single client validation source.
* Recharts for charts (donut, lines, bars).
* Local UI state: React state / `useReducer`; lightweight global UI state (theme, selected period) via context or Zustand if needed — never for server data.
* Data fetching is centralized. A single typed client (`src/api/`) wraps `fetch`, attaches the auth token, handles refresh, and maps errors to a normal shape. Feature `api.ts` files call this client; components call hooks, never `fetch` directly.
* Accessibility: semantic HTML, labelled inputs, keyboard-navigable, visible focus rings, color choices that pass WCAG AA. Charts must have a non-color-only representation (legend + values).
* i18n-ready: user-facing strings go through an i18n layer (e.g. `react-i18next`) with `pl` and `en` bundles. Primary locale `pl-PL`; never hard-code currency symbols or date formats — derive from locale + currency.
* Money display uses `Intl.NumberFormat`; integers divided by 100 only at the formatter (port `lib/money.ts` from the prototype). Never do float math on amounts.
Frontend quality bar

* Loading, empty, and error states for every async view (no silent spinners forever; no blank screens on error).
* Optimistic updates for create/delete where it improves feel; always reconcile with server truth on settle.
* A global error boundary + a toast system for recoverable errors.
* No layout shift on data load; skeletons over spinners for lists/cards.
3. Backend stack & conventions

* Java 21 (LTS), Spring Boot 3.x. Build with Gradle (Kotlin DSL) (Maven acceptable if preferred — pick one and be consistent).
* Spring Web (REST), Spring Data JPA (Hibernate), Spring Security (JWT resource-server style), Spring Validation (Jakarta Bean Validation), Flyway (migrations), springdoc-openapi (OpenAPI / Swagger UI).
* PostgreSQL in all environments; Testcontainers for integration tests (never test against H2 — its SQL dialect masks Postgres-specific bugs).
* MapStruct (or hand-written mappers) for entity↔DTO; entities never leave the service layer.
* Lombok allowed for boilerplate (getters, builders) but not to hide important logic.
Layering (strict)

```
Controller  → only HTTP concerns: routing, status codes, DTO in/out, validation triggers.
Service     → business logic, transactions (@Transactional), ownership checks, orchestration.
Repository  → Spring Data interfaces + custom queries; the ONLY place that touches the DB.
Entity      → JPA-mapped; never serialized to clients; never accepted from clients.
DTO         → request/response records; validated; the public contract.
Mapper      → entity ↔ DTO conversion.

```

Backend rules

* Controllers are thin. No business logic, no entity references, no repository calls. Each returns DTOs and proper HTTP status codes.
* `@Transactional` lives on services, not controllers or repositories. Reads that span multiple repos use `@Transactional(readOnly = true)`.
* Money is `long` minor units on entities and DTOs. A `Money` value object or `MoneyUtil` centralizes parsing/formatting/rounding. FX math may use `BigDecimal` internally but stores `long`.
* DTOs are Java `record`s with Bean Validation annotations (`@NotNull`, `@Positive`, `@Size`, `@Pattern` for currency codes, etc.).
* No business logic in the database beyond constraints and indexes. Aggregations may be SQL (efficient) but their correctness is covered by tests.
* Idempotency / determinism: import dedupe and rate resolution behave identically to the prototype (see §8, §7).
* Time: store timestamps in UTC (`timestamptz`); calendar dates as `DATE`. Never depend on server timezone for business logic.
4. API design

* Base path `/api/v1`. Version in the path; never break v1 without bumping.
* REST resources, plural nouns, predictable shapes:

```
Auth
  POST   /api/v1/auth/register           {email, password}          -> 201 + tokens
  POST   /api/v1/auth/login              {email, password}          -> 200 + tokens
  POST   /api/v1/auth/refresh            {refreshToken}             -> 200 + access token
  POST   /api/v1/auth/logout                                        -> 204
  GET    /api/v1/auth/me                                            -> current user profile

Accounts
  GET    /api/v1/accounts                ?includeArchived=
  POST   /api/v1/accounts
  GET    /api/v1/accounts/{id}
  PATCH  /api/v1/accounts/{id}
  POST   /api/v1/accounts/{id}/archive
  GET    /api/v1/accounts/{id}/balance   (only when trackBalance=true)

Categories
  GET    /api/v1/categories              (tree or flat with parentId)
  POST   /api/v1/categories
  PATCH  /api/v1/categories/{id}
  DELETE /api/v1/categories/{id}         (reassign/uncategorize on delete; see §6)

Transactions
  GET    /api/v1/transactions            ?from&to&accountId&categoryId&type&q&page&size&sort
  POST   /api/v1/transactions
  GET    /api/v1/transactions/{id}
  PATCH  /api/v1/transactions/{id}
  DELETE /api/v1/transactions/{id}
  POST   /api/v1/transactions/bulk       (bulk categorize / delete)

Reporting (read models — see §9)
  GET    /api/v1/reports/summary         ?from&to                (income/expense/net, base ccy)
  GET    /api/v1/reports/breakdown       ?from&to&parentId       (category donut + drilldown)
  GET    /api/v1/reports/trend           ?from&to&interval=month (spending over time)
  GET    /api/v1/reports/cashflow        ?from&to

Rules (auto-categorization)
  GET/POST/PATCH/DELETE  /api/v1/rules
  POST   /api/v1/rules/apply             (re-run rules over existing uncategorized txns)

Import
  POST   /api/v1/imports/preview         (multipart file + mapping) -> parsed rows + dedupe flags
  POST   /api/v1/imports/commit          -> creates importBatch + transactions
  GET    /api/v1/imports/batches
  DELETE /api/v1/imports/batches/{id}    (undo whole batch)
  GET    /api/v1/imports/profiles/{accountId}   (remembered column mapping)
  PUT    /api/v1/imports/profiles/{accountId}

Settings & FX
  GET/PUT /api/v1/settings               (reportingCurrency)
  GET    /api/v1/fx/rates                (user-managed rate table to base)
  PUT    /api/v1/fx/rates/{currency}
  GET    /api/v1/fx/rates/latest         (optional: fetch from provider; see §7)

```

Conventions

* Pagination: page/size (or cursor); list endpoints return `{ items, page, size, total }`. Default `size=50`, max `200`.
* Filtering via query params; date filters are inclusive `from`/`to` (`YYYY-MM-DD`).
* Errors: RFC 9457 `application/problem+json` — `{ type, title, status, detail, instance, errors? }`. One global `@RestControllerAdvice` maps exceptions to this shape. Validation failures return `422` with field-level `errors`.
* Status codes: `201` create, `200` read/update, `204` delete/no-content, `400` malformed, `401` unauthenticated, `403` forbidden, `404` not-found, `409` conflict (dedupe / stale version), `422` validation. For cross-user access, return `404` (don't leak existence) — apply this consistently.
* Optimistic locking with a `version` column on transactions/accounts; return `409` on stale writes.
* Idempotency-Key header honored on `POST /transactions` and `/imports/commit` to make retries safe.
* OpenAPI auto-generated and served at `/swagger-ui`; the JSON spec is the source for generating the frontend's TS types.
5. Authentication, authorization & security

* Multi-user with email + password. Passwords hashed with BCrypt (strength ≥ 12) or Argon2. Never store or log plaintext.
* JWT access tokens (short-lived, ~15 min) + refresh tokens (long-lived, rotating, revocable). Store refresh tokens server-side (hashed) so they can be revoked; access tokens are stateless.
* Token transport: prefer refresh token in an HttpOnly, Secure, SameSite cookie; access token in memory on the client (not localStorage) to limit XSS blast radius. If using header-based access tokens, document the XSS tradeoff.
* Every data query is user-scoped. Each domain table has a `user_id` FK. Repositories filter by the authenticated user id (passed from the service); there is no unscoped finder for user data. Add an integration test per resource proving user A cannot read/modify user B's rows.
* Authorization: method-level checks in services; `404`/`403` on violation. Spring Security `@PreAuthorize` is a second layer, not the primary one.
* Validation & limits: validate all input (Bean Validation + service checks); cap CSV upload size and row count; reject unknown currencies not in the user's rate table unless a rate is supplied.
* Security headers & CORS: strict CORS allowlist (frontend origin only); `Content-Security-Policy`, `X-Content-Type-Options`, `Referrer-Policy`, HSTS in prod.
* Rate limiting on auth endpoints (login/register/refresh) to slow brute force; backoff/lockout after repeated failures.
* Secrets come from environment variables / a secrets manager — never committed. The JWT signing key is a strong secret, rotated with a key-id (`kid`) scheme.
* PII & data: minimal PII (email only). Provide account deletion that purges all user data (GDPR-style "right to be forgotten") and a full data export (§10, §12).
* Audit: log security-relevant events (login success/failure, password change, data export, account deletion) without logging secrets or full financial detail.
* Dependency hygiene: CI runs vulnerability scanning (OWASP dependency-check / `npm audit` / Dependabot).
6. Data model (canonical)
Money fields are `BIGINT` minor units. All user-owned tables carry `user_id BIGINT NOT NULL REFERENCES users(id)`, plus `created_at`, `updated_at` (`timestamptz`), and `version BIGINT` for optimistic locking.
User: id, email (unique, citext), passwordHash, displayName, createdAt, status (active|disabled). Settings are 1:1 with user.
Account: id, userId, name, type (`checking|savings|cash|credit`), currency (ISO 4217), startingBalanceMinor (nullable `BIGINT`), trackBalance (bool, default `false`), archived (bool, default `false`).
Category: id, userId, name, kind (`expense|income`), parentId (nullable — two levels only; a category with a parent may not itself be a parent), color (hex). Unique (userId, parentId, name).
Transaction: id, userId, date (`DATE`, ISO calendar day), amountMinor (positive `BIGINT`, native currency), type (`expense|income|transfer`), accountId, counterAccountId (transfers only, else null), categoryId (nullable; always null for transfers), currency (ISO 4217), rateToBase (`NUMERIC` — base value = `round(amountMinor * rateToBase)`, locked at entry), description (raw text), note (free text), importBatchId (nullable), dedupeHash (text), version. Indexes: `(userId, date)`, `(userId, accountId, date)`, `(userId, categoryId)`, and dedupe support on `(userId, accountId, dedupeHash)`.
Rule: id, userId, pattern (substring, case-insensitive match on description), categoryId, priority (int; higher wins). Order: highest priority first, first substring hit wins (see prototype `matchCategory`).
ImportProfile: per (userId, accountId) remembered column mapping — delimiter, encoding, hasHeader, dateIndex, dateFormat, amountMode (`signed|debitCredit`), amountIndex, expenseIsNegative, debitIndex, creditIndex.
ImportBatch: id, userId, accountId, fileName, createdAt, count. Deleting a batch deletes its transactions (undo).
Settings: userId (1:1), reportingCurrency (default `PLN`).
FxRate: (userId, currency) -> rateToBase (`NUMERIC`). Rates are anchored to the reporting/base currency so any pair cross-converts. Optionally lastFetchedAt + source.
Schema rules

* Money: `BIGINT`. Never `numeric`/`float` for stored amounts (rates are the only `NUMERIC`).
* Referential integrity with FKs; deleting an account is blocked if it has transactions (archive instead). Deleting a category sets its transactions' `categoryId` to null (uncategorized) by default and surfaces the affected count in the confirm dialog.
* Migrations are Flyway, forward-only, immutable once merged. Never edit a shipped migration; add a new one. Seed reference data (default categories) via a migration or an idempotent seeder keyed to the user on first login.
* Two-level category constraint enforced in service + a check at insert (a category whose parent already has a parent is rejected).
7. Currency handling (carried from prototype — do not "improve" away)

* Each transaction stores its native currency AND `rateToBase`, captured at entry time. All aggregations use the base value via `baseMinor = round(amountMinor * rateToBase)`.
* Never convert live for historical totals. Locking the rate keeps past reports stable even as rates change.
* Rates are anchored to the base (reporting) currency; the reporting currency's own rate is `1`. Cross-currency works because everything resolves to base.
* The user maintains an FX rate table (`/fx/rates`). An optional provider sync fills "latest" rates, but a stored transaction's rate is never retroactively changed.
* Default reporting currency `PLN`; support multiple currencies and per-currency minor-unit exponents later (centralize the exponent like the prototype's `MINOR_UNIT_EXPONENT`; today assume 2 decimals, leave a seam for JPY=0 etc.).
* Changing the reporting currency affects display rollups going forward but does not rewrite locked per-transaction rates; document the behavior in settings.
8. CSV import (must handle Polish bank exports)
The most failure-prone feature; the prototype's hard-won handling is mandatory. Pipeline is preview → map → commit, with the mapping remembered per account.
Must handle:

* Delimiter often `;` (because comma is the decimal separator); also `,` and `\t`. Auto-detect with override.
* Decimal comma & space thousands: `"1 234,56" -> 123456` minor units; also EU `"1.234,56"` and US `"1,234.56"`. Port `parseAmountToMinor` logic exactly — sign handling, NBSP/narrow-NBSP/thin-space stripping, last-separator-wins decimal detection, half-up rounding beyond 2 dp.
* Encoding sometimes Windows-1250 (and UTF-8, ISO-8859-2). Detect/allow override; decode bytes before parsing.
* Multiple date formats: `YYYY-MM-DD`, `DD.MM.YYYY`, `DD-MM-YYYY` (+ an "auto" mode trying the common Polish ones).
* Sign convention OR separate debit/credit columns (`amountMode`: `signed` with `expenseIsNegative`, or `debitCredit` with debit/credit indexes).
* Column-mapping step, remembered per account (`ImportProfile`).
* Deduplicate via `dedupeHash` over `[date, amountMinor, currency, accountId, description]` (match the prototype's hash inputs); flag dupes in preview, skip on commit by default with a per-row override.
* Group each import as an `ImportBatch` so it is undoable (delete batch → delete its transactions).
* On commit, run the auto-categorization rules over imported rows.
The backend does parsing/decoding/dedupe (testable, consistent across clients); the frontend drives the mapping UI and preview. Cap file size and row count; stream rather than loading everything into memory for large files.
9. Reporting & "where my money goes" (the point of the app)
Read models; correctness is covered by tests with fixed fixtures.

* Period summary: income / expense / net for a date range, in base currency.
* Category breakdown (donut): spend per parent category for a period, rolling subcategories up to the parent by default; drill path parent → subcategory → transactions. Uncategorized is its own slice with a fixed color.
* Spending over time (trend): stacked/area or line by category or total, per month (or week), comparable across periods.
* Cashflow: income vs expense over time; running net.
* Filtering & search: by date range, account, category, type, and free-text on description/note (the same filters power both the list and reports).
* Period selectors: this month, last month, quarter, year, custom range; month-over-month and year-over-year comparison.
* Reports must be fast: push aggregation into SQL (`GROUP BY`) with proper indexes; do not pull all rows into the app and sum in Java at scale.
* Every reported total is in base currency minor units, formatted at the edge.
10. Cross-cutting "production grade" features
What separates a prototype from a professional app — implement incrementally but design for them from day one.

* Recurring transactions: templates that materialize on schedule (rent, salary), with edit-this/all semantics and a preview of upcoming items.
* Budgets: per-category monthly budget with progress, over/under alerts, and an optional rollover.
* Data export: CSV and JSON export of all user data; per-report CSV export.
* Data import/backup: full account export + re-import that round-trips losslessly (money as integers).
* Bulk operations: multi-select transactions → categorize / delete / assign rule.
* Saved views / filters: persist a user's favorite filter combinations.
* Soft delete + undo for destructive actions where feasible; hard delete for GDPR.
* Notifications (later): budget threshold, large transaction, recurring due — in-app first, email optional.
* Multi-device: state is server-side, so the same account works across devices; design toward an eventual offline/PWA mode but don't block v1 on it.
* Audit trail of changes to transactions (who/when/what) for trust.
11. Frontend↔backend contract & type safety

* Backend exposes OpenAPI; generate TS types from it (e.g. `openapi-typescript`) into `src/api/types.gen.ts`. Do not hand-maintain DTO types on the client.
* A single typed `apiClient` wraps `fetch`: base URL, auth header/refresh, JSON parsing, problem+json error mapping, abort signals.
* Zod schemas validate forms client-side and mirror backend constraints; keep them next to the feature.
* Shared invariants (money is integer minor units, currency is ISO 4217, dates are `YYYY-MM-DD`) are enforced on BOTH sides; never trust the client on the server.
12. Configuration, environments & data lifecycle

* Profiles: `dev`, `test`, `prod` (`application-{profile}.yml`). No secrets in files; inject via env. `dev` uses docker-compose Postgres; `test` uses Testcontainers; `prod` uses managed Postgres.
* 12-factor: config from environment, stateless processes, logs to stdout.
* Frontend env: `VITE_API_BASE_URL` etc.; no secrets in the bundle.
* Backups: documented DB backup/restore; the data export endpoint doubles as a user-level backup. Account deletion purges all rows transactionally.
* Migrations run automatically on backend startup (Flyway) and are validated in CI against a fresh DB.
13. CI/CD, Docker & deployment

* Docker: multi-stage builds. Backend → JRE-slim image running the fat JAR; frontend → build with Node, serve static via nginx. Root `docker-compose.yml` brings up `db + backend + frontend` (one command to run the whole app locally).
* CI (GitHub Actions) on every PR:
   * Backend: `./gradlew build` → compile, unit tests, slice tests, Testcontainers integration tests, Flyway validation, static analysis (Spotless/Checkstyle), dependency vulnerability scan, JaCoCo coverage gate.
   * Frontend: `npm ci` → typecheck (`tsc --noEmit`), `eslint`, `vitest run`, `npm run build`, `npm audit`.
   * Build Docker images; optionally run an E2E smoke test against compose.
* CD: build & push images on merge to main; deploy to the target environment. Migrations gate the deploy.
* Quality gates: a PR cannot merge if lint, typecheck, tests, or coverage thresholds fail.
14. Observability & operations

* Structured logging (JSON in prod) with correlation/request IDs; never log secrets, tokens, or full PII.
* Spring Boot Actuator: `/health` (liveness/readiness), `/info`, `/metrics`.
* Metrics via Micrometer → Prometheus-friendly endpoint; key business metrics (imports run, txns created) plus system metrics.
* Tracing (optional) via OpenTelemetry.
* Error tracking (optional) via Sentry on both ends.
* Graceful error handling: the global handler returns problem+json; unexpected errors return `500` without leaking stack traces to clients (logged server-side with an id the user can quote).
15. Testing strategy (required, not optional)
Backend

* Unit tests for money parsing/formatting/rounding, FX resolution, dedupe hashing, rule matching, CSV row parsing (port the prototype's test cases: decimal comma, space thousands, EU/US separators, Windows-1250, date formats).
* Repository/slice tests (`@DataJpaTest`) against Testcontainers Postgres.
* Web slice tests (`@WebMvcTest`) for controller contracts, validation, and error mapping.
* Integration tests (`@SpringBootTest` + Testcontainers) covering auth flows and — critically — cross-user isolation (user A cannot touch user B's data) for every resource.
* Aggregation correctness: fixed-fixture tests asserting summary/breakdown/ trend numbers to the grosz.
Frontend

* Unit tests (Vitest) for `lib/money`, formatters, csv helpers.
* Component tests (Testing Library) for forms, validation, list/empty/error states.
* MSW to mock the API; test loading/error/success and optimistic updates.
* E2E (Playwright) for the core loop: register → add account → add txn → see it in summary and breakdown; import CSV → preview → commit → undo.
Coverage: enforce a meaningful threshold (e.g. 80% backend services + utils); prioritize money, import, reporting, and auth paths over UI chrome.
16. Build phases (do in order, STOP after each for review)
Mirror the prototype's incremental discipline; each phase ships something usable and reviewable.

1. Foundations: repo scaffold (backend + frontend + compose), Postgres + Flyway baseline, auth (register/login/refresh, JWT, user scoping), settings, health checks, CI green. Acceptance: a user can sign up, log in, and hit a protected `/me`.
2. Core loop: accounts CRUD, manual transaction entry, transaction list, this-period income/expense/net summary (base currency). Acceptance: add an account + transactions, see correct totals.
3. Breakdown views: category CRUD (two-level), donut + drill-down + period selector; reporting endpoints with SQL aggregation. Acceptance: spend rolls up to parent and drills to transactions.
4. CSV import + rules engine: preview/map/commit, Polish-bank handling, dedupe, import batches + undo, auto-categorization rules + re-apply. Acceptance: a real Polish bank CSV imports cleanly and is categorized.
5. Trend + search/filtering: spending-over-time, full filtering, free-text search, comparisons.
6. Recurring transactions + export: recurring templates; CSV/JSON export and full-data backup.
7. Hardening: budgets, observability, rate limiting, security headers, coverage gates, E2E, deployment polish.
Within each phase: write the migration + entity, the service with ownership + tests, the controller + DTOs + validation, the OpenAPI surface, then the frontend feature (api → hooks → components) with its loading/empty/error states and tests. Don't move to the next phase with a red build.
17. Definition of done (per feature)

* Migration added (forward-only) and applied cleanly to a fresh DB.
* Service enforces ownership + invariants; covered by unit + isolation tests.
* DTOs validated; controller returns correct status codes + problem+json errors.
* OpenAPI updated; frontend types regenerated.
* Frontend has loading/empty/error/success states, form validation, and tests.
* Money is integer minor units end-to-end; formatting only at the edges.
* Lint + typecheck + tests + coverage pass in CI.
* No secret committed; no `any`; no entity leaked to the client.
18. Coding conventions quick reference

* TS: `strict`, no `any`, no non-null `!` unless justified; exhaustive `switch` on unions; prefer `type`-only imports; absolute imports via `@/`.
* Java: constructor injection (no field `@Autowired`); package-by-feature; immutable DTO records; `final` where it helps; no business logic in controllers/entities; meaningful exceptions mapped centrally.
* Naming: REST resources plural; DB tables snake_case; Java camelCase; React components PascalCase; hooks `useX`.
* Commits/PRs: small, phase-scoped; the PR description states which build phase and acceptance criteria it satisfies.
* Comments explain why (especially money, FX, and CSV edge cases), not what.
19. Commands
Frontend (`frontend/`)

* `npm run dev` — Vite dev server
* `npm run build` — typecheck + production build
* `npm run lint` — ESLint
* `npm test` / `npm run test:watch` — Vitest
* `npm run gen:api` — regenerate TS types from backend OpenAPI
Backend (`backend/`)

* `./gradlew bootRun` — run (dev profile)
* `./gradlew build` — compile + all tests + checks
* `./gradlew test` — tests only
* `./gradlew flywayMigrate` / `flywayInfo` — migrations
Whole app

* `docker compose up --build` — db + backend + frontend locally
20. What NOT to do

* Do not store money as float/double/`numeric`, or do float arithmetic on amounts.
* Do not let controllers touch repositories/entities, or return entities to clients.
* Do not write an unscoped query over user data.
* Do not convert currencies live for historical totals or rewrite locked rates.
* Do not edit a shipped Flyway migration.
* Do not put secrets in the repo or the frontend bundle.
* Do not skip the import edge cases (decimal comma, Windows-1250, `;` delimiter, debit/credit columns, dedupe, undoable batches).
* Do not skip ahead in the build phases or merge with a red build.