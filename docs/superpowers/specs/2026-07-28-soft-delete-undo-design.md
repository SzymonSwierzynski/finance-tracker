# Soft-delete / undo (transactions) — Design Spec

**Date:** 2026-07-28
**Branch:** `backlog-soft-delete-undo` (to be created off `main`)
**Status:** Approved design, ready for implementation planning
**Related:** `CLAUDE.md` §10 (optional backlog — "soft-delete/undo"), §4 (domain rules), §6 (API); backlog-completion program item **C** (order A→H).

---

## 1. Goal

Deleting a transaction should be **reversible**: it moves to a **Trash** instead of being destroyed, an
**Undo** toast offers immediate restore, and a Trash view lists deleted items to Restore or delete
forever. Trashed rows are auto-purged after a retention window.

The hard constraint (the app's core promise, `CLAUDE.md` §1): a deleted transaction must **disappear
from every read path** — lists, all reports, budgets, account balances, export/backup, and import
dedupe — or a "deleted" expense would still distort where-the-money-goes.

**Scope:** transactions only (not accounts/categories). One new migration: **V15**.

---

## 2. Semantics

- **Delete = soft-delete.** `DELETE /transactions/{id}` sets `deleted_at = now` (was a hard delete).
  The row still exists but is invisible to all active reads.
- **Undo + Trash.** The client shows a time-limited "Undo" toast (calls restore); deleted rows also
  live in a **Trash** view to Restore or permanently delete later.
- **Restore** clears `deleted_at`; the transaction reappears everywhere.
- **Permanent delete** hard-deletes a trashed row ("delete forever").
- **Retention purge** hard-deletes trashed rows older than `app.trash.retention` (**default 30d**),
  on a nightly schedule (mirrors `RefreshTokenCleanup` / `IdempotencyKeyCleanup`).
- **Dedupe excludes deleted.** A soft-deleted row does not block re-entering or re-importing an
  identical transaction — deleting then re-adding recreates it.
- **Import batch-undo is unchanged** (a hard cascade delete of the batch's rows). "Undo this import"
  is a distinct bulk action from per-row trash; keeping it hard avoids trash filling with whole imports.

---

## 3. Approach — explicit per-query filtering (not a global restriction)

Add `deleted_at IS NULL` to each active-read query rather than Hibernate `@SQLRestriction` /
`@SoftDelete` on the entity. Rationale:
- A global restriction hides deleted rows from **every** entity query, so **Trash and Restore would
  have to bypass it with native queries** — awkward and error-prone.
- It does **not** touch native SQL anyway; the 5 native aggregates would still need manual filtering.
- Explicit filtering is transparent and each change is pinned by a test — consistent with the
  codebase's "SQL aggregates, correctness pinned by fixed-fixture tests" ethos (`CLAUDE.md` §9/§13).

**Completeness is the risk.** The implementation plan will `grep` for **every** `transactionRepository`
call and every `FROM transactions` native query and check each is either active-filtered or an
intentional trash/restore/purge query. Known read paths (from exploration):

| Query | Where | Change |
|-------|-------|--------|
| `findAll(Specification)` (list) | `TransactionService.list` | add `cb.isNull(deletedAt)` predicate |
| `summarize` (native) | summary report | `AND t.deleted_at IS NULL` |
| `accountActivityMinor` (native) | account balance | `AND t.deleted_at IS NULL` |
| `sumByCategory` (native) | breakdown, budgets | `AND t.deleted_at IS NULL` |
| `sumByPeriod` (native) | cashflow/trend | `AND t.deleted_at IS NULL` |
| `sumByPeriodAndCategory` (native) | category trend, budget carry | `AND t.deleted_at IS NULL` |
| `findDedupeHashesByUserIdAndAccountId` (JPQL) | import dedupe | `AND t.deletedAt IS NULL` |
| `findByIdAndUserId` | get / update / soft-delete target | → `findByIdAndUserIdAndDeletedAtIsNull` |
| `findByUserIdOrderByDateAscIdAsc` | export/backup | → `...AndDeletedAtIsNull...` |
| `countByUserIdAndCategoryIdIn` | category-delete count | → `...AndDeletedAtIsNull` |
| `findByUserIdAndCategoryIdIsNullAndTypeIn` | rules re-apply | → `...AndDeletedAtIsNull` |
| recurring materializer's existing-check | `RecurringTransactionService` | verify + exclude deleted (planning) |

---

## 4. Backend

### Migration — `V15__transactions_soft_delete.sql`
```sql
ALTER TABLE transactions ADD COLUMN deleted_at TIMESTAMPTZ;
-- Hot path is active rows; partial index keeps active scans lean and supports the trash query.
CREATE INDEX idx_transactions_active ON transactions (user_id, date) WHERE deleted_at IS NULL;
CREATE INDEX idx_transactions_trash ON transactions (user_id, deleted_at) WHERE deleted_at IS NOT NULL;
```
`Transaction` entity gains `private Instant deletedAt;` (`@Column(name = "deleted_at")`).

### Repository
- Rename/adjust the active finders to `...AndDeletedAtIsNull` (table above); add the native/JPQL
  `deleted_at IS NULL` clauses.
- New: `Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNotNull(id, userId)` (restore/permanent
  target), `Page<Transaction> findByUserIdAndDeletedAtIsNotNull(userId, Pageable)` (trash list, sorted
  by `deletedAt` desc), and a purge `@Modifying` delete `WHERE deleted_at < :cutoff`.

### Service (`TransactionService`)
- `delete(userId, id)` → load active, set `deletedAt = Instant.now()`, `saveAndFlush`.
- `restore(userId, id)` → load **deleted**, clear `deletedAt`, save, return `TransactionResponse`.
- `permanentlyDelete(userId, id)` → load **deleted**, `repository.delete(...)`.
- `listTrash(userId, page, size)` → paged `TransactionResponse` (reuse `PageResponse`).
- `list(...)` Criteria filter gains the `deletedAt IS NULL` predicate.

### Controller (`TransactionController`)
- `DELETE /{id}` (soft-delete, 204), **new** `POST /{id}/restore` (200 + body), **new**
  `GET /trash?page&size` (paged), **new** `DELETE /{id}/permanent` (204).

### Cleanup + properties
- `TransactionTrashCleanup` — `@Component`, nightly `@Scheduled(cron = "${app.trash.cleanup-cron:0 45 3 * * *}")`,
  `@Transactional`, purges `deleted_at < now − retention`.
- `TrashProperties` (`@ConfigurationProperties("app.trash")`, `@ConfigurationPropertiesScan`) —
  `retention` (default 30d) + `cleanupCron`.

---

## 5. Frontend

- **List delete** → soft-delete mutation + an **"Undo"** toast whose action calls restore. If the
  `Toast` component has no action affordance, add a minimal optional action (label + onClick); keep it
  small and token-based.
- **Trash page** — new route `/transactions/trash` + a nav entry (or a link/tab from Transactions):
  paged list of trashed rows with **Restore** and **Delete forever**, loading/empty/error states.
- **Hooks/api** — `useDeleteTransaction` stays (now soft); add `useRestoreTransaction`,
  `useTrash`, `usePermanentlyDeleteTransaction`. Invalidation mirrors create: transactions + reports +
  account balances (a restore/delete affects all three); the trash list also invalidates on
  restore/permanent.
- **i18n** — PL + EN strings (undo, restored, trash title/empty, restore, delete forever, confirm).

---

## 6. Testing

### Backend — `TransactionSoftDeleteTest` (integration, `AbstractIntegrationTest` style)
The **core guarantee**, one fixed-fixture test: seed a user with an account, a category, a budget, an
FX rate and a handful of transactions; delete one; assert it is **absent from all of**:
list, `GET /reports/summary`, `/breakdown`, `/trend`, `/cashflow`, `/comparison`, budget `spentMinor`,
`GET /accounts/{id}/balance`, `GET /export/backup`, and import **dedupe** (re-importing the same row
recreates it). Then **restore** and assert every one of those reflects it again. Plus:
- Trash lists the deleted row (and not active ones); restore/permanent target only deleted rows (404
  otherwise); permanent-delete and the cleanup purge remove it; `DELETE` on an already-deleted id → 404.
- Per-user isolation (unique users, per-user assertions — shared-DB pitfall §13).

Existing suites (`ReportingSummaryTest`, `BreakdownTest`, `BudgetIsolationTest`, `ExportTest`,
`ImportPipelineTest`, `AccountIsolationTest` balance) must stay green — they already assert to the
grosz, so any missed read path fails them.

JaCoCo gate stays **0.85**; new service/repo/cleanup code covered.

### Frontend
- Light Vitest on the trash hook/api or the undo-toast wiring if a helper is extracted.
- Committed E2E stays lean; a one-off throwaway delete→undo / trash→restore check at the boundary.

---

## 7. Build & rollout order

Branch `backlog-soft-delete-undo` off `main`, backend-then-frontend, committed **separately**, **only
when the user asks** (§17):

1. **Backend:** V15 + entity + repository (active filters + trash/restore/purge finders) + the 5
   native/JPQL filters + service (soft-delete/restore/permanent/trash) + controller + cleanup/props +
   `TransactionSoftDeleteTest` → `./gradlew build` (JDK 21) green → commit `feat(backend): soft-delete + undo/trash for transactions`.
2. **Frontend:** hooks/api + undo toast + Trash page + nav + i18n → `npm run lint && npm test && npm run build` green → commit `feat(frontend): …`.
3. One-off throwaway delete→undo and trash→restore check; delete it.
4. **Stop at the phase boundary** for in-app testing. **Push only when the user asks.**
5. Update `HANDOFF.md` + `CLAUDE.md` migration number (local-only, never committed).

---

## 8. Out of scope (YAGNI)

- Soft-delete for accounts/categories (categories already have their own delete → uncategorize;
  accounts have archive). Only transactions.
- Changing import batch-undo to soft-delete (stays a hard cascade).
- A global "empty trash" bulk endpoint (per-row Delete-forever + the retention purge suffice).
- Undo for edits (only delete/restore).
- A `deleted_by` / audit trail (that's backlog item **G**).
