# API Reference

Base path **`/api/v1`**. JSON in/out; errors are RFC 9457 `application/problem+json`. Pagination via
`page` / `size` / `sort`. Enum query params are **lowercase** (`expense`, not `EXPENSE`). The
authoritative, always-current schema is the OpenAPI doc at `/v3/api-docs` (Swagger UI at
`/swagger-ui.html`).

## Auth

Public except `/me`.

| Method & path | Purpose |
|---------------|---------|
| `POST /auth/register` | Create a user; sets the refresh cookie, returns the access token |
| `POST /auth/login` | Log in; sets the refresh cookie, returns the access token |
| `POST /auth/refresh` | Rotate the single-use refresh cookie, return a new access token |
| `POST /auth/logout` | Revoke the current session |
| `GET  /auth/me` | Current user profile |

## Settings

| Method & path | Purpose |
|---------------|---------|
| `GET /settings` · `PUT /settings` | Read / update the reporting currency |

## Accounts

| Method & path | Purpose |
|---------------|---------|
| `GET /accounts?includeArchived=` | List accounts |
| `POST /accounts` | Create |
| `GET /accounts/{id}` · `PATCH /accounts/{id}` | Read / update |
| `POST /accounts/{id}/archive` | Archive |
| `GET /accounts/{id}/balance` | Balance (when `trackBalance=true`) |

## Categories

| Method & path | Purpose |
|---------------|---------|
| `GET /categories?kind=expense\|income` | List |
| `POST /categories` · `PATCH /categories/{id}` | Create / update |
| `DELETE /categories/{id}` | Delete → `{ uncategorizedCount }` |

## Transactions

| Method & path | Purpose |
|---------------|---------|
| `GET /transactions?from&to&accountId&categoryId&type&q&page&size&sort` | Filter / search / page |
| `POST /transactions` | Create (transfers require `counterAccountId`, null `categoryId`) |
| `GET /transactions/{id}` · `PATCH` · `DELETE` | Read / update / delete |

## FX rates

| Method & path | Purpose |
|---------------|---------|
| `GET /fx/rates` | `{ baseCurrency, rates }` |
| `PUT /fx/rates/{currency}` | Upsert (422 if currency == reporting currency) |
| `DELETE /fx/rates/{currency}` | Remove |

## Reporting (read models)

| Method & path | Purpose |
|---------------|---------|
| `GET /reports/summary?from&to` | Income / expense / net for a range |
| `GET /reports/breakdown?from&to&kind&parentId` | Category breakdown with subcategory roll-up |
| `GET /reports/trend?from&to&interval=month\|week[&kind=]` | Trend (category-stacked when `kind` set) |
| `GET /reports/cashflow?from&to&interval=` | Cashflow with running net |
| `GET /reports/comparison?from&to&mode=month\|year` | MoM / YoY period comparison |
| `GET /reports/trend-comparison?from&to` | Current vs the immediately-preceding **equal-length** period, with expense category movers |

## Rules & import

| Method & path | Purpose |
|---------------|---------|
| `GET/POST/PATCH/DELETE /rules` · `POST /rules/apply` | Manage and apply auto-categorization rules |
| `POST /imports/preview` · `POST /imports/commit` | CSV preview / commit |
| `GET /imports/batches` · `DELETE /imports/batches/{id}` | List / undo import batches |
| `GET/PUT /imports/profiles/{accountId}` | Per-account remembered column mapping |

## Recurring

| Method & path | Purpose |
|---------------|---------|
| `GET/POST/PATCH/DELETE /recurring` | Manage templates |
| `POST /recurring/run` | Materialize the caller's due templates (a nightly sweep covers all users) |

## Export / backup / restore

| Method & path | Purpose |
|---------------|---------|
| `GET /export/transactions` · `GET /export/transactions/csv` | Export transactions |
| `GET /export/backup` | Full JSON backup (name-referenced) |
| `POST /export/restore` | Additive, idempotent restore (dedupes; transfers round-trip) |

## Budgets

| Method & path | Purpose |
|---------------|---------|
| `GET /budgets?month=YYYY-MM` | Progress with subcategory roll-up + over/under |
| `POST /budgets` | Create (expense category; 409 if one already exists) |
| `PATCH /budgets/{id}` · `DELETE /budgets/{id}` | Update / delete |

## Actuator

| Method & path | Purpose |
|---------------|---------|
| `GET /actuator/{health,info,prometheus}` | Health, info, metrics (Prometheus requires auth in prod) |

Every response carries an `X-Request-Id` correlation header.
