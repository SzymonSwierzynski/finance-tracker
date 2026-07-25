# Frontend — Finance Tracker SPA

Vite + React 19 + TypeScript (strict) single-page app for the Finance Tracker. Talks to the Spring
Boot API at `/api/v1`. See the root [README](../README.md) for the whole stack and
[docs/architecture.md](../docs/architecture.md) for the system design.

## Stack

- **Vite 8** (build + dev server, HMR)
- **React 19** + **TypeScript** (strict)
- **Tailwind v4** — semantic CSS-variable tokens, class-based dark mode
- **TanStack Query** — server state, caching, invalidation
- **React Router 8** — routing (classic component API)
- **React Hook Form + Zod** — forms and validation
- **i18next** — pl-PL (primary) + en
- **Recharts** — charts (colors read from theme tokens)
- **Vitest** — unit tests · **Playwright** — E2E

## Scripts

```bash
npm run dev        # Vite dev server on :5173 (proxies /api → :8080)
npm run build      # tsc -b (typecheck) then vite build → dist/
npm run preview    # serve the production build locally
npm run lint       # eslint .
npm test           # vitest run (unit)
npm run test:watch # vitest watch
npm run e2e         # Playwright E2E (needs the real stack up — see docs/development.md)
npm run gen:api    # regenerate src/api/types.gen.ts from the running backend's OpenAPI
```

## Structure

```
src/
  api/          client.ts (single fetch wrapper: auth + one-shot refresh + ApiError),
                types.gen.ts (generated from OpenAPI — do not hand-edit), types.ts (curated aliases)
  app/          App, AppLayout, route guards
  components/   shared UI primitives (StatCard, skeletons, …)
  features/     feature folders: auth, accounts, transactions, categories, breakdown,
                trends, reports, budgets, recurring, import, rules, fx, dashboard, settings
  lib/          cross-cutting: money, date, i18n, color, theme
```

## Conventions

- **Data flow:** components → hooks (TanStack Query) → feature `api.ts` → `src/api/client.ts`.
  Never `fetch` from a component.
- **Money is integer minor units;** format only at the display edge via `lib/money.ts`.
- **Auth:** the access token lives in memory only (never `localStorage`); the refresh token is an
  HttpOnly cookie. `client.ts` retries once on a 401 via `POST /auth/refresh`.
- **Generated types are `T | undefined`, not `null`** — omit optional fields rather than sending null.
- **Dark mode** is class-based Tailwind v4 with semantic tokens; prefer tokens over scattered
  `dark:` variants. Charts read colors from the tokens.
- Mutations invalidate the right query keys (a transaction invalidates reports + account balances; a
  restore invalidates everything).

## Lockfile note

`package-lock.json` must stay **cross-platform complete**. Do not regenerate it with a bare
`npm install` on macOS — that prunes Linux-only optional deps (`@emnapi/*`) and breaks `npm ci` on
Linux CI. Regenerate inside Linux instead (see [docs/development.md](../docs/development.md)).
