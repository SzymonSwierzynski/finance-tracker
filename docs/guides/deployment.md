# Deployment

## Docker Compose (whole stack)

```bash
docker compose up --build
```

Three services:

| Service    | Image / build     | Port | Notes |
|------------|-------------------|------|-------|
| `db`       | `postgres:16-alpine` | `127.0.0.1:5432` | Data persisted in the `db-data` volume |
| `backend`  | `./backend`       | `127.0.0.1:8080` | Localhost-only (for dev curl / Swagger); the frontend reaches it over the internal compose network (`backend:8080`) |
| `frontend` | `./frontend` (`nginx:1.27-alpine`) | `5173:80` | Serves the SPA + reverse-proxies `/api` |

The app is available at **<http://localhost:5173>**.

> The backend port is bound to `127.0.0.1` on purpose: it's reachable for local dev but not exposed
> on the network, so the unauthenticated docs/metrics and the rate limiter's client-IP handling can't
> be reached bypassing nginx.

## Same-origin architecture

The frontend container is a multi-stage build (`node:24-slim` builds the SPA → `nginx:1.27-alpine`
serves `dist/`). nginx:

- serves the SPA with history fallback, and
- reverse-proxies `/api` → `backend:8080` (resolver + `$request_uri`, so nginx boots before the
  backend is up and survives a backend restart).

Because the SPA and API are the **same origin**, the refresh-token cookie is first-party. This is the
key reason the stack is fronted by nginx rather than exposing the backend port directly.

## Configuration

- **JWT keys** — RS256. In dev an ephemeral key pair is generated if env keys are absent; provide a
  real key pair in production.
- **Datasource** — `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD`. The datasource uses
  `stringtype=unspecified` (required for citext email comparisons).
- **Rate limiting** — `APP_RATE_LIMIT_ENABLED` (disabled in tests / E2E; enabled in production).
- **CORS / cookie** — dev allows `http://localhost:5173` with a `SameSite=Lax`, `secure:false`
  cookie. In production the same-origin nginx setup means CORS is not needed for the SPA.

## Observability

- `GET /actuator/{health,info,prometheus}`. Every response carries an `X-Request-Id` correlation id;
  logs are JSON with the request id. Prometheus scraping requires auth in production.

## CI gates before deploy

CI (`.github/workflows/ci.yml`) must be green: backend build + coverage, frontend
lint/test/build/audit, and the Playwright E2E job. See [development.md](development.md#ci).
