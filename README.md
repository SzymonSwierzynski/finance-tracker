# Personal Finance Tracker

Multi-user personal finance app — *see where my money goes*. React + Tailwind SPA over a Spring
Boot REST API on PostgreSQL. The build contract is [`CLAUDE.md`](CLAUDE.md); the original Dexie
prototype lives under [`reference/`](reference/).

> **Status — Phase 2 (Core loop), backend.** On top of the Phase 1 foundations (auth, settings,
> money/error model, security, OpenAPI, CI): accounts CRUD + archive + balance, manual transactions
> (with FX rate locked at entry, transfers and dedupe hashing), filtered/paged transaction listing,
> and the period income/expense/net summary in base currency. The React frontend lands in a later
> round.

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

## Core loop (Phase 2)

```bash
AUTH='Authorization: Bearer <ACCESS_TOKEN>'

# Create an account (money is integer minor units; 1000.00 PLN starting balance)
curl -s -X POST localhost:8080/api/v1/accounts -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Checking","type":"checking","currency":"PLN","trackBalance":true,"startingBalanceMinor":100000}'

# Add transactions (rateToBase is locked at entry; PLN resolves to 1, foreign currencies require a rate)
curl -s -X POST localhost:8080/api/v1/transactions -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"date":"2024-06-01","amountMinor":250000,"type":"income","accountId":1,"description":"Salary"}'

# See where the money goes (income / expense / net, in base currency)
curl -s "localhost:8080/api/v1/reports/summary?from=2024-06-01&to=2024-06-30" -H "$AUTH"
```

## Key invariants (from the contract)

- **Money is integer minor units** (`long` / `BIGINT`); division by 100 only at the display edge.
- **Every query is user-scoped**; cross-user access returns `404`. Proven by isolation tests.
- **Errors are RFC 9457 problem+json**; validation failures are `422` with field errors.
- **Migrations are Flyway, forward-only**; never edit a shipped migration.
