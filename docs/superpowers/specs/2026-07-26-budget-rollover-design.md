# Budget rollover — Design Spec

**Date:** 2026-07-26
**Branch:** `backlog-budget-rollover` (to be created off `main`)
**Status:** Approved design, ready for implementation planning
**Related:** `CLAUDE.md` §10 (optional backlog — "budget rollover: needs a `rollover` column via V13 + carry-over in `BudgetService`"), §6 (budgets API), §16 (phases); `HANDOFF.md` §17 point 3. First item (**A**) of the §10 backlog-completion program (order A→H).

---

## 1. Goal

Let a per-category monthly budget **carry its unspent balance forward** month over month, so a light
month builds a cushion for a heavier one — instead of every month starting fresh at the fixed limit.

Rollover is **opt-in per budget**. It is **off by default**, and an off budget behaves **exactly** as
today (strict month-to-month: `GET /budgets?month=` compares that month's spend against the fixed
limit, nothing carried). Users who want a strict budget are unaffected.

This is **purely additive**: no behavior changes for existing budgets until a user turns rollover on.

---

## 2. Carry model — "envelope with floor" (compounding, no debt)

When rollover is **on**, a month's **available** amount is the fixed limit plus the accumulated
carry from prior months; the carry **compounds** and is **floored at zero** (a heavy month can draw
the cushion down but never creates a negative "debt" month):

```
available(m) = limit + carriedIn(m)
carriedIn(creationMonth) = 0
carriedIn(m+1)           = max(0, limit + carriedIn(m) − spent(m))     // the floored fold
remaining(m)             = available(m) − spent(m)                     // may be negative (over)
over(m)                  = spent(m) > available(m)
```

- `limit` = the budget's stored `amountMinor` (base-currency minor units).
- `spent(m)` = base-currency expense spend for category in month `m`, with subcategory spend rolled
  up into a parent budget — the **same** rollup `BudgetService.rollUpExpenses` / `breakdown()` use.
- The floor is `max(0, …)`, so overspending a month reduces the carried cushion but never below 0.

### Worked reference (limit = 500/month, budget created in January)

| Month | Spent | carriedIn | available | remaining | carry→next = max(0, avail−spent) |
|------:|------:|----------:|----------:|----------:|--------------------------------:|
| Jan   | 400   | 0         | 500       | +100      | 100 |
| Feb   | 550   | 100       | 600       | +50       | 50  |
| Mar   | 620   | 50        | 550       | −70 (over)| **0** (floored) |
| Apr   | 300   | 0         | 500       | +200      | 200 |

This table is the canonical backend fixture (§6, asserted to the grosz).

---

## 3. Anchor & the month-agnostic simplification

- **Anchor = the budget's creation month.** `carriedIn = 0` for that month and any earlier month;
  the fold runs forward from creation. No extra schema beyond the `rollover` flag.
- For the common flow (create budget → enable rollover), creation ≈ now, so the cushion builds from
  zero going forward — the intuitive behavior.
- Because the carry is floored (model §2), turning rollover on for an **older** budget can only ever
  surface **accumulated past savings — never a phantom deficit**, so the retroactive case is benign.
- **Accepted v1 simplification:** budgets store only the *current* limit (no per-month amount
  history), so editing a budget's limit **recomputes carry for past months at the new limit**. This
  is acceptable for v1; per-month amount history is explicitly out of scope (§8).

---

## 4. UI (approved)

- **`BudgetForm`** gains a **"Roll over unused budget"** checkbox (label + short helper text),
  defaulting to unchecked. It flows into create and update. No other form change.
- **`BudgetsPage`** — for a budget with rollover on:
  - Show the **carried-in** amount near the limit, e.g. *"+1,00 zł carried"* (formatted via
    `Money` / `lib/money`, reporting currency, PL-primary).
  - Base the **progress bar** on `available = limit + carriedIn` (so a budget with a cushion reads as
    more headroom), while still flagging `over` when `spent > available`.
  - A rollover budget is visually marked (small badge/icon) so the carried figure is legible.
  - Rollover-**off** budgets render exactly as today.
- Token-based styling throughout; dark-mode aware.

---

## 5. Backend

### Migration — `V13__budgets_rollover.sql`
```sql
ALTER TABLE budgets ADD COLUMN rollover BOOLEAN NOT NULL DEFAULT FALSE;
```
Forward-only. Existing rows default `false` → strict behavior, unchanged. (V13 is the next free
number per `CLAUDE.md` §5.)

### Entity
`Budget` gains `private boolean rollover;` (`@Column(name = "rollover", nullable = false)`).

### DTOs
- `CreateBudgetRequest` + `UpdateBudgetRequest` gain `boolean rollover` (defaults false when omitted;
  OpenAPI-optional → treat absent as false).
- `BudgetsResponse.BudgetProgress` gains **`boolean rollover`** and **`long carriedInMinor`**.
  `remainingMinor` and `over` are redefined against `available = amountMinor + carriedInMinor` —
  **identical when `carriedInMinor == 0`**, so the change is backward-compatible for off budgets.
  (`availableMinor` is derivable as `amountMinor + carriedInMinor`; the frontend computes it, so it
  is not added to the DTO.)
- `BudgetResponse` (single-budget create/update result) gains `rollover` so the form round-trips it.

### Repository — reuse the existing per-period query
**No new query.** `TransactionRepository.sumByPeriodAndCategory(userId, from, to, fmt, type)` already
returns per-`(to_char(t.date, :fmt), category_id)` base-minor sums for one kind — it backs the
category-stacked trend. Call it with `fmt = 'YYYY-MM'`, `type = 'expense'` over the historical range to
get per-(month, category) expense sums, which is exactly the fold input. Base amount is the locked
`SUM(round(amount_minor * rate_to_base))` — identical to `sumByCategory`, so the historical numbers
agree with the requested-month rollup. This is strategy (i); reusing the proven query keeps scope
minimal and matches conventions (§17). (Note: the transaction date column is `date`.)

### Service — `BudgetService.list(userId, month)`
1. Requested-month `spent` per budget: **unchanged** (existing `rollUpExpenses` for `month`).
2. If **no** budget has `rollover` on → behave exactly as today (`carriedIn = 0` everywhere; the
   month-grouped query is **not** run — off budgets pay nothing extra).
3. If ≥1 rollover budget: call `sumByPeriodAndCategory(userId, from, to, 'YYYY-MM', 'expense')`
   **once**, where `from = min(creationMonth of rollover budgets).atDay(1)` and
   `to = month.minusMonths(1).atEndOfMonth()`. Parse each row's `'YYYY-MM'` period to `YearMonth`,
   build a `(YearMonth, categoryId) → base` map with the **same subcategory→parent rollup** as
   `rollUpExpenses`, then for each rollover budget fold forward from its own creation month (§2
   formula, months in ascending order, gaps = 0 spend) to get `carriedIn(month)`.
4. Build `BudgetProgress` with `available`, `remaining`, `over`, `rollover`, `carriedInMinor`.

The floored fold (§2) is extracted as a **pure function** `RolloverCalculator.carriedIn(creationMonth,
targetMonth, limit, spentByMonth)`, so it is unit-tested deterministically against the §2 table with no
Spring/DB. The budget's creation month is derived from `created_at` **at UTC** (§14 UTC-anchor pitfall).

`create` / `update` persist `rollover` from the request. All other invariants unchanged (expense-only,
one-per-category 409, optimistic-lock version via `saveAndFlush`, cross-user → 404).

### Controller
No new endpoint — `GET /budgets`, `POST /budgets`, `PATCH /budgets/{id}` carry the new field through
their existing methods.

---

## 6. Frontend

- **Types:** add `rollover` + `carriedInMinor` to the budget types in `api/types.ts` (hand-declared
  interim, matching the existing budget/restore note) and **`npm run gen:api`** (backend up) to source
  them from OpenAPI.
- **`features/budgets/`:**
  - `BudgetForm.tsx` — add the rollover checkbox (React Hook Form + Zod), wire into create/update.
  - `BudgetsPage.tsx` — render carried amount + available-based progress bar + rollover badge.
  - `api.ts` / `hooks.ts` — pass `rollover` through; TanStack Query invalidation unchanged (budgets
    key). No new endpoint call.
- **i18n:** new PL + EN strings in `lib/i18n.ts` (checkbox label + helper, "carried", rollover badge
  tooltip). Money/label keys reused.

---

## 7. Testing

### Backend — two layers
**Unit — `RolloverCalculatorTest` (pure, deterministic):** the §2 Jan→Apr table asserted to the grosz
(carry per month, incl. the Mar floor at 0 and the Apr reset), plus target month ≤ creation → 0, and
gap months (no spend) accruing the full limit.

**Integration — new `BudgetRolloverTest` (`BudgetIsolationTest` style):** because the anchor is the
freshly-created budget's `created_at` (≈ now), the fixture uses **now-relative months**
(`base = YearMonth.now(UTC)`; expenses in `base…base+3`, queried at each of `base…base+3`) — a hardcoded
past month would sit *before* creation and never fold. Asserts the compounding scenario end-to-end,
plus **rollover off → carriedIn 0, identical to today** and **subcategory rollup folds into carry**.
Cross-user isolation is already covered by `BudgetIsolationTest` (no new resource/table — just a
column), so it is not duplicated.

JaCoCo instruction-coverage gate stays **0.85**; new service/calculator/DTO code must be covered.

### Frontend
- Light Vitest if any carry-formatting helper is extracted (e.g. available/remaining/over display).
- Committed E2E stays lean (core-loop + budgets only). If useful, extend the **existing** `budgets`
  E2E or do a **one-off** throwaway rollover check (temp spec → run → delete), not a new committed spec.

---

## 8. Build & rollout order

On a new `backlog-budget-rollover` branch off `main`, backend-then-frontend, committed **separately**,
**only when the user asks** (§17 standing rules):

1. **Backend:** V13 migration + entity + DTOs + repository query + service fold + `BudgetRolloverTest`
   → `cd backend && ./gradlew build` (JDK 21) green → commit `feat(backend): budget rollover`.
2. **Frontend:** types (+ `gen:api`) + form checkbox + page display + i18n
   → `cd frontend && npm run lint && npm test && npm run build` green → commit `feat(frontend): …`.
3. One-off Playwright pass exercising the checkbox + carried display, then delete the temp spec.
4. **Stop at the phase boundary** for the user to test in-app. **Push only when the user asks.**
5. Update `HANDOFF.md` (state + roadmap) — local-only, never committed.

---

## 9. Out of scope (YAGNI)

- **Model B (debt / negative carry)** and **model C (one-month, non-compounding)** — decided against.
- **Per-month amount history** — the accepted v1 simplification recomputes past carry at the current
  limit (§3).
- A **global** rollover setting — rollover is strictly per budget.
- Rollover on **income** budgets — budgets remain expense-only.
- Any change to the reporting/breakdown/cashflow endpoints — rollover is contained to budgets.
