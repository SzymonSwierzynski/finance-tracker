# Bulk transaction ops — Design Spec

**Date:** 2026-07-31
**Branch:** `backlog-bulk-transaction-ops` (to be created off `main`)
**Status:** Approved design, ready for implementation planning
**Related:** `CLAUDE.md` §10 (optional backlog — "bulk transaction ops"), §4 (domain rules), §6 (API); backlog-completion program item **D** (order A→H). Builds on item C soft-delete (bulk delete = bulk soft-delete).

---

## 1. Goal

Let the user act on **many transactions at once** from the transaction list: multi-select rows, then
**delete** (soft, undoable) or **recategorize** them in one action — instead of editing one at a time.

**Scope:** two actions — **recategorize** and **delete** — plus **bulk-restore** to back the undo toast.
**Move-account is out of scope** (currency + transfer edge cases; a possible follow-up). No new migration.

---

## 2. Semantics

- **Atomic / all-or-nothing.** Each bulk action validates the **whole** selection first; if anything is
  invalid, it changes **nothing** and returns an error. Cross-user or wrong-state id → **404**;
  a kind mismatch or a transfer in a recategorize → **422**.
- **Bulk delete** = bulk soft-delete (sets `deleted_at`), so deleted rows land in the Trash (item C) and
  are restorable. **Bulk restore** clears `deleted_at`.
- **Bulk recategorize** sets one `categoryId` on all selected rows. `categoryId = null` uncategorizes.
  A non-null category must be **owned**, and every selected transaction must be **non-transfer** and of
  the **matching kind** (a category is expense or income; the whole selection must be that kind).
- **No per-row optimistic version.** A bulk selection carries no client versions, so bulk ops skip the
  `version` check (unlike single PATCH). Accepted simplification — bulk edits are coarse by nature.
- **Bounded.** `ids` is capped (`@Size(max = 500)`) so a request can't be unbounded.
- Selection is **client-side over the loaded rows** (the current page), not a server-side "all matching
  the filter" — keeps the contract a simple id list.

---

## 3. Approach — three focused endpoints

`POST /transactions/bulk-delete`, `…/bulk-restore`, `…/bulk-categorize` — each a small `{ ids }`
(+ `categoryId`) body. Chosen over a single `/bulk` action-enum endpoint: only two actions (+ restore),
each with distinct validation and payload, so focused endpoints are clearer and need no discriminator.
(If move-account is added later it can be a fourth endpoint or trigger a refactor to the enum shape.)

---

## 4. Backend

### DTOs (`transaction/dto/`)
- `BulkIdsRequest(@NotEmpty @Size(max = 500) List<Long> ids)` — delete + restore.
- `BulkCategorizeRequest(@NotEmpty @Size(max = 500) List<Long> ids, Long categoryId)` — null = uncategorize.
- `BulkResult(int affected)`.

### Repository (`TransactionRepository`)
- `List<Transaction> findByIdInAndUserIdAndDeletedAtIsNull(Collection<Long> ids, long userId)` — active targets (delete / categorize).
- `List<Transaction> findByIdInAndUserIdAndDeletedAtIsNotNull(Collection<Long> ids, long userId)` — trashed targets (restore).

### Service (`TransactionService`)
- `bulkDelete(userId, ids)`: load active-owned by ids; if `size != distinct(ids).size` → `NotFoundException` (some id unowned/not active); set `deletedAt = now` on each; `saveAll`; return `affected`.
- `bulkRestore(userId, ids)`: load trashed-owned by ids; validate count; clear `deletedAt`; `saveAll`.
- `bulkCategorize(userId, ids, categoryId)`: load active-owned by ids; validate count; if `categoryId != null` resolve the owned category once and require every tx to be non-transfer with matching kind (reuse the `resolveCategoryId` invariants, hoisted to validate a set) — else `UnprocessableEntityException`; set `categoryId` (or null) on each; `saveAll`.
- De-duplicate the incoming `ids` (a `Set`) so `count` comparison is exact.

### Controller (`TransactionController`)
Three `@PostMapping`s (`/bulk-delete`, `/bulk-restore`, `/bulk-categorize`) returning `BulkResult`,
`@Valid @RequestBody`. Errors flow through the existing `GlobalExceptionHandler` (404 / 422 problem+json).

No migration, no scheduled work — this is pure API + service over existing tables.

---

## 5. Frontend

- **Selection.** A checkbox column on the transaction list rows + a header "select all (this page)"
  checkbox; selection state is a `Set<number>` of ids (cleared on page/filter change).
- **Action bar.** A sticky bar appears when ≥1 selected: **"N selected"**, **Clear**, **Delete**, and a
  **Recategorize** control (a category `<select>` → apply). Delete → `bulk-delete` + an **Undo** toast
  that `bulk-restore`s the same ids. Recategorize is **disabled with a hint** when the selection is
  mixed-kind or includes a transfer, so the 422 path is rare (the backend still enforces it).
- **Hooks/api.** `useBulkDelete`, `useBulkRestore`, `useBulkCategorize`; invalidation mirrors single ops
  (transactions + reports + account balances).
- **i18n.** PL + EN strings (selected count, clear, delete, recategorize, bulk-deleted/undo, errors).

---

## 6. Testing

### Backend — `BulkTransactionOpsTest` (integration, `AbstractIntegrationTest` style)
- **Delete:** bulk-delete N → all gone from the list, present in Trash; `affected == N`. Atomic: a batch
  containing an unowned id or an already-deleted id → **404**, nothing deleted.
- **Categorize:** bulk-categorize N to an expense category → all updated; `categoryId = null`
  uncategorizes. Atomic: an income tx (or a transfer) in an expense-category batch → **422**, nothing
  changed; an unowned id → **404**.
- **Restore:** bulk-restore round-trips the deleted ids back into the active reads.
- **Per-user isolation** — a user cannot bulk-act on another's ids (shared-DB pitfall §13: unique users,
  per-user assertions).
- JaCoCo gate stays **0.85**; new service/DTO code covered.

### Frontend
- Light Vitest on the selection helper (toggle / select-all / clear) if extracted.
- Committed E2E stays lean; a one-off select→delete→undo and select→recategorize check at the boundary.

---

## 7. Build & rollout order

Branch `backlog-bulk-transaction-ops` off `main`, backend-then-frontend, committed **separately**,
**only when the user asks** (§17):

1. **Backend:** DTOs + repository finders + service (bulkDelete/Restore/Categorize) + controller +
   `BulkTransactionOpsTest` → `./gradlew build` (JDK 21) green → commit `feat(backend): bulk transaction ops (delete/restore/categorize)`.
2. **Frontend:** selection + action bar + hooks/api + i18n → `npm run lint && npm test && npm run build` green → commit `feat(frontend): …`.
3. One-off throwaway select→delete→undo and select→recategorize check; delete it.
4. **Stop at the phase boundary** for in-app testing. **Push only when the user asks.**
5. Update `HANDOFF.md` + `CLAUDE.md` (local-only; no migration this time — next free stays **V16**).

---

## 8. Out of scope (YAGNI)

- **Move-account** (currency + transfer edges) — possible follow-up.
- Server-side "select all matching the current filter" (only the loaded-page ids).
- Bulk edit of amount/date/note; bulk ops on accounts/categories.
- Optimistic-lock/version enforcement on bulk edits.
