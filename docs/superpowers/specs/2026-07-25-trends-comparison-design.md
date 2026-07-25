# Trends period-comparison — Design Spec

**Date:** 2026-07-25
**Branch:** `phase-7-trends-comparison`
**Status:** Approved design, ready for implementation planning
**Related:** `HANDOFF.md` §17 (point 4); `CLAUDE.md` §6 (reporting endpoints), §16 (phases)

---

## 1. Goal

On the **Trends** tab, let the user compare the selected period against the immediately preceding
period of equal length and surface **how spending shifts** — which categories rose or fell, and by how
much — richer than the Dashboard's single per-metric delta.

This is **purely additive**: the existing Trends page (Total/Category view toggle, month/week grouping,
date presets, and the chart) is unchanged. When the comparison toggle is **off** (the default), the page
renders byte-for-byte as it does today.

**Scope:** "Option B" — summary strip **+ category movers**. A previous-period chart overlay ("Option C")
is explicitly out of scope and can be added later.

---

## 2. Comparison basis — "previous period, equal length"

The "previous" period is the contiguous range of the **same length** immediately before the selected
range (not a fixed month/year shift like the Dashboard's MoM/YoY). This is intuitive for any range the
Trends tab shows — including its default "This year".

```
days     = DAYS.between(from, to) + 1     // inclusive length of the selected range
prevTo   = from.minusDays(1)              // day before the selection
prevFrom = prevTo.minusDays(days - 1)     // same length, immediately preceding
```

Examples:
- `This month` (Jul 1–31) → previous = Jun 1–30
- `This year` (Jan 1–Jul 25) → previous = the equal-length span ending Dec 31 last year
- Custom 90 days → the prior 90 days

UI label: **"vs previous period"** (the Trends tab does not reuse the Dashboard's "vs last month/year").

---

## 3. UI (approved layout)

The comparison layers onto the existing page. Render order in `TrendsPage`:

1. **Existing** header + controls (Total/Category, month/week) — unchanged.
2. **Existing** date presets row, **plus** a new right-aligned **"Compare to previous period"**
   on/off toggle (default **off**).
3. **New** summary strip: **Income / Expenses / Net** cards, each with Δ absolute + Δ% vs the previous
   period. Coloring: red = worse, green = better (expense ▲ = red, income ▼ = red, net ▼ = red).
4. **Existing** chart — full width, unchanged (Total = composed income/expense/net; Category = stacked).
5. **New** highlight line + **category movers** panel, full width below the chart.

All three new pieces render **only when the toggle is on and data exists**. Off → they disappear entirely.

### Category movers
- **Expense categories only**, rolled up to their **top-level parent** (same rollup as the Breakdown donut).
- One row per parent present in **either** period: category dot/color, name, current spend, and
  Δ (absolute + %) with ▲ (spent more, red) / ▼ (spent less, green).
- **Sorted by absolute change**, biggest mover first. All active categories shown.
- Edge labels (composed at the display edge, not stored):
  - previous = 0 → **"new"** (no percentage)
  - current = 0 → **"gone"**
  - null category → **"Uncategorized"**
  - parent id whose category was since deleted → **"Unknown"** (parity with `breakdown()`)

### Highlight line
One sentence composed **frontend-side** from the overall expense delta + the top mover, via an i18n
template, e.g. *"Expenses up 12% (+3 420) vs the previous period — biggest driver: Eating out (+1 240)."*

### States
- Loading: skeletons for the strip + movers (the chart keeps its own loading state).
- Previous period empty → every mover reads "new".
- Neither period has expense activity → movers panel shows a small "no comparable activity" note.
- Styling is token-based (dark-mode aware) throughout.

---

## 4. Backend

### Endpoint
`GET /api/v1/reports/trend-comparison?from&to` — auth-scoped, `requireRange(from, to)` validation
(shared with the other reporting endpoints). No `mode` param; the previous period is always the
equal-length preceding range (§2). No new query params beyond `from`/`to`; movers are expense-only.

### DTO — `TrendComparisonResponse`
Reuses the existing `PeriodSummary` + `Delta` records from `ComparisonResponse` (one source of truth).

```java
record TrendComparisonResponse(
    String currency,
    PeriodSummary current,     // income/expense/net, base minor units
    PeriodSummary previous,
    Delta delta,               // current − previous per total
    List<CategoryMover> movers)

record CategoryMover(
    Long categoryId,           // null = Uncategorized
    String name,
    String color,
    long currentMinor,
    long previousMinor,
    long deltaMinor)           // current − previous
```

All money is integer minor units in the reporting currency. **Percentages are derived at the display
edge, never computed/stored as floats** — consistent with the existing comparison contract.

### Service — `ReportingService.trendComparison(userId, from, to)`
- **Strip:** reuse the existing private `periodSummary(...)` for the current and previous ranges and
  build `Delta` — identical to how `comparison()` already works.
- **Movers:** call `transactionRepository.sumByCategory(userId, range, "expense")` for **each** range
  (no new SQL — the existing query, called twice), roll subcategory rows up to their top-level parent
  using the category map (the same rollup `breakdown()` performs), producing `parentKey → baseMinor`
  for current and previous. Union the keysets, emit one `CategoryMover` per parent, sorted by
  `abs(deltaMinor)` descending.
- **Refactor:** extract the parent-rollup step into a small private helper so `breakdown()` and the
  movers share it instead of duplicating.

### Controller
One new method in `ReportingController`, mirroring the existing reporting endpoints.

### Migration
**None.** Read-only report over existing tables — no schema change, no V13.

---

## 5. Frontend

- **Hook:** `useTrendComparison(from, to, enabled)` in `features/reports/hooks.ts`, mirroring
  `useComparison` (query key `['reports', 'trend-comparison', { from, to }]`, `enabled` gated on the
  toggle so it only fetches when on).
- **Types:** add `TrendComparison` + `CategoryMover` to `api/types.ts`, hand-declared for now with a
  note to `npm run gen:api` (backend up) to source them from OpenAPI — same interim pattern as the
  budget/restore types.
- **`TrendsPage.tsx`** stays the orchestrator; extract focused components under `features/trends/`:
  - `ComparisonToggle` — the on/off control; new `const [compare, setCompare] = useState(false)`.
  - `ComparisonStrip` — income/expenses/net cards with Δ; reuses a shared delta/percent + coloring
    helper lifted into `lib/` (rather than duplicating the Dashboard's logic).
  - `CategoryMovers` — highlight line + ranked list.
- **i18n:** new PL + EN strings in `lib/i18n.ts`: toggle label, "vs previous period", "new"/"gone", the
  highlight template, the movers panel title. Income/expense/net keys already exist.
- Token-based styling; dark-mode aware.

---

## 6. Testing

### Backend — new `TrendComparisonTest` (fixed-fixture, `ComparisonTest`/`BreakdownTest` style)
- Equal-length previous window computed correctly, including partial ranges and month-length edges
  (e.g. a 31-day selection vs a 30-day preceding month — the day-count math, not a calendar shift).
- Strip income/expense/net + delta to the grosz for both periods.
- Movers rolled to top-level parent and sorted by absolute delta.
- **new** (prev = 0) and **gone** (cur = 0) categories present with correct values; Uncategorized
  bucket; deleted category → "Unknown".
- Empty ranges → zero summaries + empty movers.
- Cross-user isolation (unique registered users, per-user assertions — shared-DB pitfall).
- JaCoCo instruction-coverage gate stays at **0.85**; new service code must be covered.

### Frontend
- Light Vitest on the shared delta/percent helper (worse/better coloring, "new"/"gone", highlight
  composition).
- **One-off** Playwright toggle check (temp spec → run → delete). The committed E2E set stays lean
  (core-loop + budgets only).

---

## 7. Build & rollout order

On the existing `phase-7-trends-comparison` branch, backend-then-frontend, committed separately:

1. **Backend:** DTO + service (+ rollup refactor) + controller + `TrendComparisonTest` →
   `cd backend && ./gradlew build` green → commit `feat(backend): Trends period-comparison`.
2. **Frontend:** types + hook + components + i18n + wire into `TrendsPage` →
   `cd frontend && npm run lint && npm test && npm run build` green → commit `feat(frontend): …`.
3. One-off Playwright pass exercising the toggle, then delete the temp spec.
4. **Stop at the phase boundary** for the user to test in-app. **Push only when the user asks.**

---

## 8. Out of scope (YAGNI)

- Chart overlay of the previous period (Option C) — deferrable follow-up.
- Income movers, per-category drill-down / click-through to filtered transactions.
- Dashboard-style fixed MoM/YoY on Trends (superseded by the equal-length basis).
- Any new persistence, migration, or saved comparison preferences.
