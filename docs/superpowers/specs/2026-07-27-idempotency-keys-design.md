# Idempotency keys — Design Spec

**Date:** 2026-07-27
**Branch:** `backlog-idempotency-keys` (to be created off `main`)
**Status:** Approved design, ready for implementation planning
**Related:** `CLAUDE.md` §10 (optional backlog — "idempotency keys on `POST /transactions` + `/imports/commit`"), §6 (API), §11 (errors); backlog-completion program item **B** (order A→H).

---

## 1. Goal

Make the two create-heavy POSTs safe to retry: a client that sends the same **`Idempotency-Key`**
header twice (network retry, refresh-retry, flaky proxy, double-submit) gets the **first result
replayed** instead of a duplicate row.

- **`POST /api/v1/transactions`** (201 + `TransactionResponse`)
- **`POST /api/v1/imports/commit`** (201 + `CommitResponse`)

The header is **optional**: absent → today's behavior, so the change is fully backward-compatible.
Scope is limited to these two endpoints.

---

## 2. Semantics

- **Optional header.** No `Idempotency-Key` → the operation runs exactly as today (no record written).
- **Replay on match.** A key seen before whose **request fingerprint matches** returns the stored
  first response (same 201 + body, same `Location` for transactions) and creates nothing new.
- **Strict mismatch → 422.** A key reused with a **different** request (fingerprint differs) →
  `422 Unprocessable` "Idempotency-Key already used with a different request." (reuses
  `UnprocessableEntityException`; §11 problem+json).
- **Per user + per endpoint.** The key is scoped by `(user_id, scope)` where `scope` is
  `"transaction"` or `"import-commit"`, so the same key on different endpoints never collides. Cross-user
  is impossible (every row is `user_id`-scoped, §4.3).
- **Only successes are stored.** The record and the created row commit together (§3); if the operation
  fails (validation, 422, etc.) the whole transaction — including the claim — rolls back, so **errors
  are never replayed** and a failed key is free to retry.

### Request fingerprint
SHA-256 (hex) over the **canonical** request, taken from the already-deserialized DTO (not the raw
bytes) so client-side key ordering is irrelevant:
- transaction: `sha256(objectMapper.writeValueAsString(CreateTransactionRequest))`
- import-commit: `sha256(accountId + objectMapper.writeValueAsString(ImportMapping) + fileBytes)`

---

## 3. Concurrency & atomicity (the core)

`IdempotencyService.execute(...)` runs **inside the caller's existing `@Transactional`**, so the
idempotency record, the created entity, and the stored response all commit as one unit.

```
T execute(long userId, String scope, String key, String fingerprint, Class<T> type, Supplier<T> op):
    if key == null || key.isBlank():          return op.get()          // idempotency off
    claimed = repo.tryClaim(userId, scope, key, fingerprint)           // INSERT ... ON CONFLICT DO NOTHING
    if claimed == 1:                                                   // we own the key
        T result = op.get()
        repo.storeResponse(userId, scope, key, toJson(result))         // UPDATE response_body
        return result
    rec = repo.find(userId, scope, key)                               // committed row from a prior/concurrent req
    if !rec.fingerprint.equals(fingerprint):  throw UnprocessableEntityException(...)   // 422
    return fromJson(rec.responseBody, type)                            // replay
```

- **`INSERT … ON CONFLICT (user_id, scope, idempotency_key) DO NOTHING`** is used deliberately: unlike a
  raw unique-violation it does **not** abort the Postgres transaction, so the follow-up `find` works in
  the same tx.
- **Two concurrent requests, same key:** the second `INSERT` **blocks on the unique index** until the
  first transaction commits, then sees the conflict (0 rows) and replays the now-committed response.
  No "in-progress" state is ever visible; no double-execute; no orphan rows (a rolled-back op rolls back
  its claim too).
- **Single-instance note (mirrors `RefreshTokenCleanup`):** correctness relies only on Postgres, so this
  is safe across scaled-out instances too (the DB serializes the claim).

---

## 4. Backend

### Migration — `V14__idempotency_keys.sql`
```sql
CREATE TABLE idempotency_keys (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope               TEXT        NOT NULL,
    idempotency_key     TEXT        NOT NULL,
    request_fingerprint TEXT        NOT NULL,
    response_body       TEXT,        -- JSON of the response DTO; null only mid-transaction (never seen committed)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_keys_uq UNIQUE (user_id, scope, idempotency_key)
);
CREATE INDEX idx_idempotency_keys_created ON idempotency_keys (created_at);
```
Both endpoints are 201-create, so no `response_status` column — the controllers already emit 201 on
replay. (V14 is the next free number per `CLAUDE.md` §5 after V13 = budget rollover.)

### New package `com.financetracker.common.idempotency`
- **`IdempotencyKey`** standalone `@Entity` (id, user_id, scope, idempotency_key, request_fingerprint,
  response_body, created_at — **not** `UserOwnedEntity`; no version/updated_at needed) +
  **`IdempotencyKeyRepository`** (`JpaRepository`) with:
  - `int tryClaim(userId, scope, key, fingerprint)` — `@Modifying` native `INSERT … ON CONFLICT DO NOTHING`, returns rows affected.
  - `int storeResponse(userId, scope, key, responseBody)` — `@Modifying` native `UPDATE`.
  - `Optional<Row> find(userId, scope, key)` — scoped finder (projection of fingerprint + response_body).
  - `int deleteCreatedBefore(Instant cutoff)` — for the purge.
- **`IdempotencyService`** — the `execute(...)` above; depends on the repo + `ObjectMapper`. Serialize
  failures wrap as an internal error (500) — they shouldn't happen for these DTOs.
- **`Fingerprints`** small helper — `sha256Hex(byte[]...)` / `sha256Hex(String...)` (JDK `MessageDigest`).

Lives under `common/` because it is cross-cutting infrastructure like `hash/`, `web/`, `observability/`.

### Wiring the two endpoints
- **Controllers:** add `@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey`
  and pass it to the service. No status/Location changes (identical on replay).
- **`TransactionService.create(userId, request, idempotencyKey)`** wraps its current body:
  `return idempotencyService.execute(userId, "transaction", idempotencyKey, fingerprint,
  TransactionResponse.class, () -> <existing create logic>);` — still `@Transactional`.
- **`ImportService.commit(userId, accountId, name, bytes, mapping, idempotencyKey)`** likewise with
  scope `"import-commit"` and `CommitResponse.class`.

### Retention — `IdempotencyKeyCleanup`
Mirrors `RefreshTokenCleanup`: `@Component`, nightly `@Scheduled(cron = "${app.idempotency.cleanup-cron:0 30 3 * * *}")`,
`@Transactional`, deletes rows with `created_at < now - ttl` (`app.idempotency.retention`, **default 48h**).
Bind via an `IdempotencyProperties` record (like `AuthProperties`).

---

## 5. Frontend

- **`api/client.ts`:** `RequestOptions` gains `idempotencyKey?: string`; in `send()`, when present set
  `finalHeaders['Idempotency-Key'] = opts.idempotencyKey`. Because `send()` is re-invoked on the 401
  refresh-retry, the same key rides the retry automatically. Works for JSON and FormData (it's a header).
- **Call sites generate a key per submit** with `crypto.randomUUID()`:
  - transaction create (the `useCreateTransaction` mutationFn / its `api.post`),
  - import commit (the wizard's commit call).
  A network/refresh retry of that submit reuses the key; the submit button is already disabled while
  pending, covering rapid double-clicks. (Perfect double-click dedupe across *separate* submits would
  need a persisted per-form key — out of scope; the backend still protects if a key repeats.)
- No visible UI change; no new strings.

---

## 6. Testing

### Backend — `IdempotencyIsolationTest` (integration, `AbstractIntegrationTest` style)
- **Replay:** same key + same body twice → both return **201 with the identical body**, and only **one**
  transaction exists (assert list count / that ids match).
- **Mismatch:** same key + different body → **422**.
- **No header:** two identical requests without a key → **two** rows (unchanged behavior).
- **Import commit:** same key replays the `CommitResponse` and imports the batch **once**.
- **Per-user isolation:** the same key string for two users is independent (shared-DB pitfall §13 —
  register unique users, assert per-user).
- **Cleanup:** `deleteCreatedBefore` removes old rows, keeps fresh ones.
- Concurrency is covered structurally by the unique constraint; a focused test may fire two claims and
  assert one insert + one replay (best-effort — the DB serializes regardless).

### Unit
- `Fingerprints.sha256Hex` stability (same input → same hex; different input → different).
- `IdempotencyService.execute` decision table with a mocked repo (off / claim / replay / mismatch).

### Frontend
- Light Vitest asserting `api.post(..., { idempotencyKey })` sends the `Idempotency-Key` header (mock `fetch`).
- Committed E2E stays lean (core-loop + budgets only); optional one-off throwaway check at the boundary.

JaCoCo instruction-coverage gate stays **0.85**; new service/repo/cleanup code must be covered.

---

## 7. Build & rollout order

On a new `backlog-idempotency-keys` branch off `main`, backend-then-frontend, committed **separately**,
**only when the user asks** (§17):

1. **Backend:** V14 migration + `common/idempotency` (entity/repo/service/fingerprints/cleanup/props)
   + wire the two controllers/services + tests → `cd backend && ./gradlew build` (JDK 21) green →
   commit `feat(backend): idempotency keys on transaction + import-commit`.
2. **Frontend:** `client.ts` option + the two call sites + a header unit test →
   `cd frontend && npm run lint && npm test && npm run build` green → commit `feat(frontend): …`.
3. One-off Playwright/throwaway check that a repeated submit doesn't duplicate; delete it.
4. **Stop at the phase boundary** for in-app testing. **Push only when the user asks.**
5. Update `HANDOFF.md` + `CLAUDE.md` migration number (local-only, never committed).

---

## 8. Out of scope (YAGNI)

- Replaying **error** responses (only committed 201s are stored).
- Idempotency on other endpoints (budgets/accounts/restore) — only the two §10 names it.
- A `response_status` column (both endpoints are 201-create).
- A persisted per-form key for perfect cross-submit double-click dedupe (button-disable + backend guard suffice).
- A distributed lock — Postgres serializes the claim; revisit only if a purge/claim becomes a hotspot.
