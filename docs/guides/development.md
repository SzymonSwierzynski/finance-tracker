# Development

## Prerequisites

- **JDK 21** — the Gradle toolchain is pinned to 21. Gradle's Kotlin DSL breaks on Java 25 locally,
  so make sure 21 is active:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
  ```
- **Node 24+** and **Docker** (for Postgres and Testcontainers).

## Local dev loop (three processes)

```bash
# 1. Postgres
docker compose up -d db

# 2. Backend — http://localhost:8080
cd backend && ./gradlew bootRun

# 3. Frontend — http://localhost:5173 (Vite proxies /api → :8080)
cd frontend && npm install && npm run dev
```

## Testing

### Backend

```bash
cd backend && ./gradlew build
```

Runs Spotless, unit + slice + Testcontainers integration tests, Flyway validation, and the
**JaCoCo instruction-coverage gate (≥ 0.85)** on non-boilerplate classes.

- Add an **isolation test for every new user-owned resource** (cross-user access must 404).
- Reporting tests assert fixed-fixture totals **to the grosz**.
- Integration tests share **one singleton Testcontainers Postgres with no rollback** (data persists
  between tests): register unique users, and for global/cross-user queries assert per-user outcomes
  (`>= n`), never an exact global count.
- The rate limiter is disabled in the base integration test (`app.rate-limit.enabled=false`); one
  suite re-enables it with a per-test `remoteAddr`.

### Frontend

```bash
cd frontend
npm run lint      # eslint
npm test          # vitest unit tests
npm run build     # tsc -b typecheck + vite build
```

**E2E (Playwright)** drives Chromium against the real stack (SPA → Vite proxy → Spring Boot →
Postgres). Bring the backend and Postgres up first, then:

```bash
npm run e2e
```

The committed E2E set is intentionally **lean** (core loop + budgets). Ad-hoc "check everything" runs
are one-off: create a temp spec, run it, delete it — don't commit new specs.

## Conventions

- **Backend:** strict layering (Controller → Service → Repository); entities never leave the service
  layer; DTOs are Java records with Bean Validation. Enum query params are lowercase (`expense`) —
  `WebConfig` registers the converters.
- **Frontend:** components → hooks → feature `api.ts` → `src/api/client.ts`. Forms use React Hook
  Form + Zod. Regenerate API types with `npm run gen:api` (backend running) — never hand-edit
  `src/api/types.gen.ts`.
- See [architecture.md](../reference/architecture.md) for the full domain rules and invariants.

## Regenerating the frontend lockfile (important)

`frontend/package-lock.json` must stay **cross-platform complete**. A bare `npm install` on macOS
reconciles the lock against the darwin `node_modules` and **prunes Linux-only optional deps**
(`@emnapi/*`, the wasm-binding subtree). Linux CI's `npm ci` then fails with
*"package.json and package-lock.json not in sync / Missing @emnapi/…"*.

After any `frontend/package.json` change, regenerate the lock inside Linux:

```bash
cd frontend
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$PWD":/app -w /app \
  node:24 sh -c "npm install --package-lock-only --no-audit --no-fund"
# verify Linux npm ci passes:
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$PWD":/app -w /app \
  node:24 sh -c "rm -rf node_modules && npm ci"
# then restore your local (darwin) node_modules:
npm ci
```

## CI

`.github/workflows/ci.yml` has three jobs, all on JDK 21 / clean `npm ci`:

- **backend** — `./gradlew build` (Spotless, tests, Flyway, coverage gate).
- **frontend** — `npm ci` → `lint` → `test` → `build` → `npm audit --omit=dev --audit-level=high`.
  The audit is production-deps-only (dev-only advisories in the OpenAPI type-gen tree must not block
  every PR); tighten to include dev deps once that tree is clean.
- **e2e** — Postgres service + `bootRun` (`APP_RATE_LIMIT_ENABLED=false`) + health wait + Playwright.

Dependabot watches gradle, actions, and npm.

## Working style

- Backend first, then frontend; keep both green by actually running the builds. Commit backend and
  frontend separately.
- Commit only when asked; push only when asked.
