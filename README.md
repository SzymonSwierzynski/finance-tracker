# Personal Finance Tracker

Multi-user personal finance app — *see where my money goes*. React + Tailwind SPA over a Spring
Boot REST API on PostgreSQL. The build contract is [`CLAUDE.md`](CLAUDE.md); the original Dexie
prototype lives under [`reference/`](reference/).

> **Status — Phase 1 (Foundations), backend.** Auth (register / login / refresh / logout / me),
> per-user settings, money utilities, error model, security, OpenAPI, migrations, tests and CI.
> The React frontend lands in the next round.

## Layout

```
backend/   Spring Boot 3.5 (Java 21, Gradle Kotlin DSL) — REST API at /api/v1
frontend/  React SPA (added in the frontend round)
reference/ the local-first Dexie prototype (domain logic is ported from here)
```

## Run the backend locally

Requires Docker. JDK 21 is provided via the Gradle toolchain.

```bash
# 1. Start Postgres
docker compose up -d db

# 2. Build + test (Spotless, unit/slice/Testcontainers integration, Flyway, coverage gate)
cd backend && ./gradlew build

# 3. Run the API (http://localhost:8080)
./gradlew bootRun
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Health: <http://localhost:8080/actuator/health>

### Whole stack

```bash
docker compose up --build   # db + backend (frontend added later)
```

## Acceptance smoke test (Phase 1)

```bash
# Register (refresh token is set as an HttpOnly cookie; access token is in the body)
curl -i -c cookies.txt -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@example.com","password":"password123"}'

# Call a protected endpoint with the access token
curl -s localhost:8080/api/v1/auth/me -H "Authorization: Bearer <ACCESS_TOKEN>"

# Refresh using the cookie, then read/update settings
curl -i -b cookies.txt -X POST localhost:8080/api/v1/auth/refresh
```

## Key invariants (from the contract)

- **Money is integer minor units** (`long` / `BIGINT`); division by 100 only at the display edge.
- **Every query is user-scoped**; cross-user access returns `404`. Proven by isolation tests.
- **Errors are RFC 9457 problem+json**; validation failures are `422` with field errors.
- **Migrations are Flyway, forward-only**; never edit a shipped migration.
