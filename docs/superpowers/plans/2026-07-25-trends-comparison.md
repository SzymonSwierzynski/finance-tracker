# Trends period-comparison Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in "compare to the previous equal-length period" view to the Trends tab — a summary strip (income/expenses/net Δ) plus a ranked list of expense-category "movers" — without changing anything that renders today.

**Architecture:** One new read-only backend endpoint `GET /api/v1/reports/trend-comparison?from&to` computes the current period, the immediately-preceding equal-length period, their per-total delta, and expense movers rolled up to top-level parent categories. The frontend adds a toggle to `TrendsPage` that, when on, fetches this endpoint and renders a summary strip (reusing the Dashboard's `StatCard`) above the existing chart and a movers panel below it. All money stays integer minor units; percentages are derived at the display edge.

**Tech Stack:** Spring Boot 3.5 / Java 21 / JPA (backend); React 19 + TypeScript + TanStack Query + Tailwind v4 + Vitest (frontend). Branch: `phase-7-trends-comparison`.

**Spec:** `docs/superpowers/specs/2026-07-25-trends-comparison-design.md`

**Deliberate deviation from the spec:** the spec suggested extracting a shared parent-rollup helper used by both `breakdown()` and the movers. On inspection, `breakdown()`'s fold also builds child slices and a synthetic "(direct)" slice, so a shared helper would over-generalize and risk destabilizing a tested path. This plan instead adds a small dedicated expense-by-parent fold for the movers and leaves `breakdown()` untouched. Same result, lower risk.

**Build order:** all backend tasks first (one `feat(backend)` commit), then frontend (a `refactor(frontend)` commit for the `StatCard` extraction, then a `feat(frontend)` commit for the feature). Do not push; stop at the phase boundary for the user to test in-app.

---

## File Structure

**Backend**
- Create: `backend/src/main/java/com/financetracker/reporting/dto/TrendComparisonResponse.java` — the response record + nested `CategoryMover`.
- Modify: `backend/src/main/java/com/financetracker/reporting/ReportingService.java` — add `trendComparison(...)`, a private `accumulateExpenseByParent(...)` fold, and a private `MoverAcc` accumulator.
- Modify: `backend/src/main/java/com/financetracker/reporting/ReportingController.java` — add the `GET /trend-comparison` method.
- Create: `backend/src/test/java/com/financetracker/reporting/TrendComparisonTest.java` — fixed-fixture integration tests.

**Frontend**
- Create: `frontend/src/components/StatCard.tsx` — the stat card extracted from `DashboardPage` (shared by Dashboard + Trends).
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx` — import `StatCard` instead of the local copy.
- Modify: `frontend/src/api/types.ts` — add `CategoryMover` + `TrendComparison`.
- Modify: `frontend/src/features/reports/hooks.ts` — add `useTrendComparison`.
- Create: `frontend/src/features/trends/movers.ts` — pure `moverState` + `moverPercent` helpers.
- Create: `frontend/src/features/trends/movers.test.ts` — Vitest for those helpers.
- Create: `frontend/src/features/trends/ComparisonStrip.tsx` — the income/expenses/net strip.
- Create: `frontend/src/features/trends/CategoryMovers.tsx` — highlight line + movers list.
- Modify: `frontend/src/features/trends/TrendsPage.tsx` — toggle state + render the two new blocks.
- Modify: `frontend/src/lib/i18n.ts` — new `trends.*` strings (en + pl).

---

## Task 1: Backend — `trend-comparison` endpoint (TDD)

**Files:**
- Test: `backend/src/test/java/com/financetracker/reporting/TrendComparisonTest.java`
- Create: `backend/src/main/java/com/financetracker/reporting/dto/TrendComparisonResponse.java`
- Modify: `backend/src/main/java/com/financetracker/reporting/ReportingService.java`
- Modify: `backend/src/main/java/com/financetracker/reporting/ReportingController.java`

Run all backend commands from the `backend/` directory with Java 21:
`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/financetracker/reporting/TrendComparisonTest.java`:

```java
package com.financetracker.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Fixed-fixture correctness for the Trends period-comparison: the "previous" period is the
 * immediately-preceding range of equal length; expense movers roll up to top-level parents and are
 * ordered by absolute change; new/gone/uncategorized buckets are represented.
 */
class TrendComparisonTest extends AbstractIntegrationTest {

  @Test
  void previousPeriodIsTheEqualLengthRangeImmediatelyBefore() throws Exception {
    RegisteredUser user = register("tc-basis@example.com", "password123");
    long account = createAccount(user);
    // Selected: Jul 1–30 (30 days) -> previous: Jun 1–30.
    income(user, account, "2026-07-10", 100000);
    expense(user, account, "2026-07-15", 30000, null);
    income(user, account, "2026-06-10", 80000);
    expense(user, account, "2026-06-15", 50000, null);
    expense(user, account, "2026-05-31", 9999, null); // before previous window — excluded
    expense(user, account, "2026-07-31", 9999, null); // after selected window — excluded

    mockMvc
        .perform(
            get("/api/v1/reports/trend-comparison?from=2026-07-01&to=2026-07-30")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("PLN"))
        .andExpect(jsonPath("$.current.incomeMinor").value(100000))
        .andExpect(jsonPath("$.current.expenseMinor").value(30000))
        .andExpect(jsonPath("$.current.netMinor").value(70000))
        .andExpect(jsonPath("$.previous.from").value("2026-06-01"))
        .andExpect(jsonPath("$.previous.to").value("2026-06-30"))
        .andExpect(jsonPath("$.previous.incomeMinor").value(80000))
        .andExpect(jsonPath("$.previous.expenseMinor").value(50000))
        .andExpect(jsonPath("$.delta.incomeMinor").value(20000))
        .andExpect(jsonPath("$.delta.expenseMinor").value(-20000))
        .andExpect(jsonPath("$.delta.netMinor").value(40000));
  }

  @Test
  void moversRollUpToParentsOrderedByAbsoluteChangeWithNewGoneAndUncategorized() throws Exception {
    RegisteredUser user = register("tc-movers@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);

    long food = createCategory(user, "Food", "expense", null);
    long restaurants = createCategory(user, "Restaurants", "expense", food);
    long transport = createCategory(user, "Transport", "expense", null);
    long shopping = createCategory(user, "Shopping", "expense", null);
    long entertainment = createCategory(user, "Entertainment", "expense", null);

    // Current period (Jul 1–30):
    expense(user, account, "2026-07-10", 3000, restaurants); // Food = 3000
    expense(user, account, "2026-07-10", 2000, transport); // Transport = 2000
    expense(user, account, "2026-07-10", 300, shopping); // Shopping = 300 (new)
    expense(user, account, "2026-07-10", 700, null); // Uncategorized = 700 (new)
    // Previous period (Jun 1–30):
    expense(user, account, "2026-06-10", 1000, food); // Food direct
    expense(user, account, "2026-06-10", 1000, restaurants); // Food = 2000 total prev
    expense(user, account, "2026-06-10", 2500, transport); // Transport = 2500
    expense(user, account, "2026-06-10", 800, entertainment); // Entertainment = 800 (gone)

    // Deltas: Food +1000, Entertainment -800, Uncategorized +700, Transport -500, Shopping +300.
    mockMvc
        .perform(
            get("/api/v1/reports/trend-comparison?from=2026-07-01&to=2026-07-30")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.current.expenseMinor").value(6000))
        .andExpect(jsonPath("$.previous.expenseMinor").value(5300))
        .andExpect(jsonPath("$.delta.expenseMinor").value(700))
        .andExpect(jsonPath("$.movers.length()").value(5))
        .andExpect(jsonPath("$.movers[0].name").value("Food"))
        .andExpect(jsonPath("$.movers[0].currentMinor").value(3000))
        .andExpect(jsonPath("$.movers[0].previousMinor").value(2000))
        .andExpect(jsonPath("$.movers[0].deltaMinor").value(1000))
        .andExpect(jsonPath("$.movers[1].name").value("Entertainment"))
        .andExpect(jsonPath("$.movers[1].currentMinor").value(0))
        .andExpect(jsonPath("$.movers[1].previousMinor").value(800))
        .andExpect(jsonPath("$.movers[1].deltaMinor").value(-800))
        .andExpect(jsonPath("$.movers[2].name").value("Uncategorized"))
        .andExpect(jsonPath("$.movers[2].categoryId").doesNotExist())
        .andExpect(jsonPath("$.movers[2].currentMinor").value(700))
        .andExpect(jsonPath("$.movers[2].previousMinor").value(0))
        .andExpect(jsonPath("$.movers[3].name").value("Transport"))
        .andExpect(jsonPath("$.movers[3].deltaMinor").value(-500))
        .andExpect(jsonPath("$.movers[4].name").value("Shopping"))
        .andExpect(jsonPath("$.movers[4].currentMinor").value(300))
        .andExpect(jsonPath("$.movers[4].previousMinor").value(0));
  }

  @Test
  void freshUserGetsZeroSummariesAndNoMovers() throws Exception {
    RegisteredUser user = register("tc-empty@example.com", "password123");
    mockMvc
        .perform(
            get("/api/v1/reports/trend-comparison?from=2026-07-01&to=2026-07-30")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.current.expenseMinor").value(0))
        .andExpect(jsonPath("$.previous.expenseMinor").value(0))
        .andExpect(jsonPath("$.movers.length()").value(0));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/reports/trend-comparison?from=2026-07-01&to=2026-07-30"))
        .andExpect(status().isUnauthorized());
  }

  private long createAccount(RegisteredUser user) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Checking\",\"type\":\"checking\",\"currency\":\"PLN\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long createCategory(RegisteredUser user, String name, String kind, Long parentId)
      throws Exception {
    String parent = parentId == null ? "" : ",\"parentId\":" + parentId;
    return id(
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\"" + parent + "}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void expense(RegisteredUser user, long account, String date, long amount, Long categoryId)
      throws Exception {
    String cat = categoryId == null ? "" : ",\"categoryId\":" + categoryId;
    tx(
        user,
        "{\"date\":\""
            + date
            + "\",\"amountMinor\":"
            + amount
            + ",\"type\":\"expense\",\"accountId\":"
            + account
            + cat
            + "}");
  }

  private void income(RegisteredUser user, long account, String date, long amount) throws Exception {
    tx(
        user,
        "{\"date\":\""
            + date
            + "\",\"amountMinor\":"
            + amount
            + ",\"type\":\"income\",\"accountId\":"
            + account
            + "}");
  }

  private void tx(RegisteredUser user, String json) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests 'com.financetracker.reporting.TrendComparisonTest'`
Expected: FAIL — the endpoint returns 404 (no `/trend-comparison` mapping), so the JSON-path assertions and status checks fail.

- [ ] **Step 3: Create the response DTO**

Create `backend/src/main/java/com/financetracker/reporting/dto/TrendComparisonResponse.java`:

```java
package com.financetracker.reporting.dto;

import com.financetracker.reporting.dto.ComparisonResponse.Delta;
import com.financetracker.reporting.dto.ComparisonResponse.PeriodSummary;
import java.util.List;

/**
 * Trends period-comparison in base (reporting) currency minor units. {@code current} is the
 * requested range; {@code previous} is the immediately-preceding range of equal length; {@code
 * delta} is {@code current − previous} per total. {@code movers} are expense categories rolled up
 * to their top-level parent, ordered by absolute change (biggest first). Percentages, and the
 * new/gone labels, are derived at the display edge — never stored as floats here.
 */
public record TrendComparisonResponse(
    String currency,
    PeriodSummary current,
    PeriodSummary previous,
    Delta delta,
    List<CategoryMover> movers) {

  /** One top-level expense category's spend now vs the previous period. {@code categoryId} null = Uncategorized. */
  public record CategoryMover(
      Long categoryId,
      String name,
      String color,
      long currentMinor,
      long previousMinor,
      long deltaMinor) {}
}
```

- [ ] **Step 4: Add the service method + fold + accumulator**

In `backend/src/main/java/com/financetracker/reporting/ReportingService.java`:

Add two imports (alongside the existing `java.time` imports at the top):

```java
import com.financetracker.reporting.dto.TrendComparisonResponse;
import com.financetracker.reporting.dto.TrendComparisonResponse.CategoryMover;
import java.time.temporal.ChronoUnit;
```

Add the public method immediately after the existing `comparison(...)` method (after its closing brace, before the private `periodSummary`):

```java
  /**
   * Trends period-comparison: the requested range vs the immediately-preceding range of equal
   * length, with the per-total delta and expense-category movers rolled up to top-level parents and
   * ordered by absolute change. Reuses the summary aggregation and {@code sumByCategory} query.
   */
  @Transactional(readOnly = true)
  public TrendComparisonResponse trendComparison(long userId, LocalDate from, LocalDate to) {
    requireRange(from, to);
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    LocalDate prevTo = from.minusDays(1);
    LocalDate prevFrom = prevTo.minusDays(days - 1);

    PeriodSummary current = periodSummary(userId, from, to);
    PeriodSummary previous = periodSummary(userId, prevFrom, prevTo);
    Delta delta =
        new Delta(
            current.incomeMinor() - previous.incomeMinor(),
            current.expenseMinor() - previous.expenseMinor(),
            current.netMinor() - previous.netMinor());

    Map<Long, Category> byId =
        categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
            .collect(Collectors.toMap(Category::getId, Function.identity()));
    // Null key = the Uncategorized bucket (LinkedHashMap permits a single null key).
    Map<Long, MoverAcc> movers = new LinkedHashMap<>();
    accumulateExpenseByParent(userId, from, to, byId, movers, true);
    accumulateExpenseByParent(userId, prevFrom, prevTo, byId, movers, false);

    List<CategoryMover> out =
        movers.values().stream()
            .map(
                m ->
                    new CategoryMover(
                        m.categoryId, m.name, m.color, m.current, m.previous, m.current - m.previous))
            .sorted((a, b) -> Long.compare(Math.abs(b.deltaMinor()), Math.abs(a.deltaMinor())))
            .toList();

    return new TrendComparisonResponse(
        settingsService.reportingCurrency(userId), current, previous, delta, out);
  }

  /**
   * Folds one range's expense rows into per-top-level-parent movers (subcategories roll up to their
   * parent; null/unknown category -> Uncategorized). {@code isCurrent} routes the sum into the
   * current or previous side of each accumulator.
   */
  private void accumulateExpenseByParent(
      long userId,
      LocalDate from,
      LocalDate to,
      Map<Long, Category> byId,
      Map<Long, MoverAcc> movers,
      boolean isCurrent) {
    for (CategorySumRow row : transactionRepository.sumByCategory(userId, from, to, "expense")) {
      long base = baseMinorOf(row.getBaseMinor());
      Long catId = row.getCategoryId();
      Category cat = catId == null ? null : byId.get(catId);

      Long key;
      String name;
      String color;
      if (cat == null) {
        key = null;
        name = "Uncategorized";
        color = UNCATEGORIZED_COLOR;
      } else if (cat.getParentId() == null) {
        key = cat.getId();
        name = cat.getName();
        color = cat.getColor();
      } else {
        Category parent = byId.get(cat.getParentId());
        key = parent != null ? parent.getId() : cat.getParentId();
        name = parent != null ? parent.getName() : "Unknown";
        color = parent != null ? parent.getColor() : UNCATEGORIZED_COLOR;
      }

      MoverAcc acc = movers.computeIfAbsent(key, k -> new MoverAcc(k, name, color));
      if (isCurrent) {
        acc.current += base;
      } else {
        acc.previous += base;
      }
    }
  }
```

Add the private accumulator class next to the existing `ParentAcc` / `SeriesAcc` classes near the bottom of the file:

```java
  /** Mutable accumulator for a top-level expense mover (current vs previous spend). */
  private static final class MoverAcc {
    private final Long categoryId;
    private final String name;
    private final String color;
    private long current;
    private long previous;

    private MoverAcc(Long categoryId, String name, String color) {
      this.categoryId = categoryId;
      this.name = name;
      this.color = color;
    }
  }
```

- [ ] **Step 5: Add the controller mapping**

In `backend/src/main/java/com/financetracker/reporting/ReportingController.java`, add the import (with the other DTO imports):

```java
import com.financetracker.reporting.dto.TrendComparisonResponse;
```

Add the method after the existing `comparison(...)` method:

```java
  @GetMapping("/trend-comparison")
  public TrendComparisonResponse trendComparison(
      @CurrentUser AuthUser user,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return reportingService.trendComparison(user.id(), from, to);
  }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests 'com.financetracker.reporting.TrendComparisonTest'`
Expected: PASS (4 tests green).

- [ ] **Step 7: Run the full backend build (Spotless + all tests + JaCoCo gate)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — Spotless formatting passes and the JaCoCo instruction-coverage gate (≥ 0.85) holds. If Spotless fails on formatting, run `./gradlew spotlessApply` and re-run `./gradlew build`.

- [ ] **Step 8: Commit the backend**

```bash
git add backend/src/main/java/com/financetracker/reporting backend/src/test/java/com/financetracker/reporting/TrendComparisonTest.java
git commit -m "feat(backend): Trends period-comparison endpoint

GET /api/v1/reports/trend-comparison?from&to — current vs the immediately
preceding equal-length period, with per-total delta and expense movers rolled
up to top-level parents, ordered by absolute change. Read-only; no migration.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Frontend — extract `StatCard` into a shared component (refactor)

The Trends summary strip reuses the Dashboard's stat card. Extract it first, keeping the Dashboard green, so the feature task just imports it.

**Files:**
- Create: `frontend/src/components/StatCard.tsx`
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

Run all frontend commands from the `frontend/` directory.

- [ ] **Step 1: Create the shared component**

Create `frontend/src/components/StatCard.tsx` with the exact component currently inlined in `DashboardPage.tsx` (lines ~17–68), adding `export` and the two imports it relies on:

```tsx
import type { ReactNode } from 'react'
import { Card } from '@/components/primitives'
import { Money, useFormatMoney } from '@/components/Money'

export function StatCard({
  label,
  minor,
  currency,
  tone,
  previousMinor,
  goodWhenUp,
  compareLabel,
}: {
  label: string
  minor: number
  currency: string
  tone: 'income' | 'expense' | 'net'
  // When set, show the change vs this previous-period value.
  previousMinor?: number
  goodWhenUp?: boolean
  compareLabel?: string
}) {
  const formatMoney = useFormatMoney()
  const ring = tone === 'income' ? 'ring-positive/20' : tone === 'expense' ? 'ring-negative/20' : 'ring-brand-200'
  // Colour the amount by tone (income green, expense red, net neutral) rather than by sign — expense
  // totals are positive numbers, so a sign-based colour would show them green like income.
  const amount = tone === 'income' ? 'text-positive' : tone === 'expense' ? 'text-negative' : ''

  let delta: ReactNode = null
  if (previousMinor !== undefined) {
    const d = minor - previousMinor
    // % only when there's a base to compare against; otherwise show the absolute change.
    const pct = previousMinor !== 0 ? (d / previousMinor) * 100 : null
    const good = d === 0 ? null : d > 0 === Boolean(goodWhenUp)
    const toneClass = good === null ? 'text-fg-subtle' : good ? 'text-positive' : 'text-negative'
    const arrow = d === 0 ? '' : d > 0 ? '▲ ' : '▼ '
    const change = d === 0 ? '—' : pct !== null ? `${Math.abs(pct).toFixed(1)}%` : formatMoney(Math.abs(d), currency)
    delta = (
      <p className={`mt-1 text-xs font-medium ${toneClass}`}>
        {arrow}
        {change} <span className="font-normal text-fg-subtle">{compareLabel}</span>
      </p>
    )
  }

  return (
    <Card className={`p-5 ring-1 ${ring}`}>
      <p className="text-sm text-fg-soft">{label}</p>
      <p className={`mt-2 text-2xl font-semibold ${amount}`}>
        <Money minor={minor} currency={currency} />
      </p>
      {delta}
    </Card>
  )
}
```

- [ ] **Step 2: Use it from the Dashboard**

In `frontend/src/features/dashboard/DashboardPage.tsx`:
1. Delete the local `function StatCard(...) { ... }` definition (the block spanning roughly lines 17–68, from `function StatCard({` through its closing `}`).
2. Delete the now-unused `type ReactNode` from the first import line, so it reads:

```tsx
import { useMemo, useState } from 'react'
```

3. Add the import next to the other `@/components` imports:

```tsx
import { StatCard } from '@/components/StatCard'
```

(Leave `Money`, `Card`, etc. imports as-is — they're still used elsewhere in the page.)

- [ ] **Step 3: Verify the Dashboard is unchanged and green**

Run: `npm run lint && npx tsc --noEmit && npm test && npm run build`
Expected: all pass. `tsc` proves there are no unused-import or type errors; the Dashboard renders identically (same component, new home).

- [ ] **Step 4: Commit the refactor**

```bash
git add frontend/src/components/StatCard.tsx frontend/src/features/dashboard/DashboardPage.tsx
git commit -m "refactor(frontend): extract StatCard into a shared component

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Frontend — types + data hook

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/features/reports/hooks.ts`

- [ ] **Step 1: Add the response types**

In `frontend/src/api/types.ts`, append to the `// --- Phase 7: budgets ---` section (after the `Budgets` interface at the end of the file):

```ts
// --- Phase 7: Trends period-comparison ---
// Hand-declared interim; run `npm run gen:api` (backend running) to source from OpenAPI.

export interface CategoryMover {
  categoryId: number | null
  name: string
  color: string
  currentMinor: number
  previousMinor: number
  deltaMinor: number
}

/** Current vs the immediately-preceding equal-length period, with expense movers. */
export interface TrendComparison {
  currency: string
  current: ComparisonPeriod
  previous: ComparisonPeriod
  delta: { incomeMinor: number; expenseMinor: number; netMinor: number }
  movers: CategoryMover[]
}
```

- [ ] **Step 2: Add the query hook**

In `frontend/src/features/reports/hooks.ts`, add `TrendComparison` to the type import block at the top:

```ts
  TrendComparison,
```

Add the hook after the existing `useComparison` function:

```ts
export function useTrendComparison(from: string, to: string, enabled = true) {
  return useQuery({
    enabled,
    queryKey: ['reports', 'trend-comparison', { from, to }] as const,
    queryFn: () =>
      api.get<TrendComparison>('/api/v1/reports/trend-comparison', { params: { from, to } }),
  })
}
```

- [ ] **Step 3: Typecheck**

Run: `npx tsc --noEmit`
Expected: PASS (no output). No commit yet — committed with the feature in Task 6.

---

## Task 4: Frontend — mover formatting helpers (TDD)

**Files:**
- Create: `frontend/src/features/trends/movers.ts`
- Test: `frontend/src/features/trends/movers.test.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/features/trends/movers.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { moverPercent, moverState } from './movers'

describe('moverState', () => {
  it('is "new" when there was no previous spend', () => {
    expect(moverState({ currentMinor: 300, previousMinor: 0, deltaMinor: 300 })).toBe('new')
  })
  it('is "gone" when there is no current spend', () => {
    expect(moverState({ currentMinor: 0, previousMinor: 800, deltaMinor: -800 })).toBe('gone')
  })
  it('is "up" when spending rose', () => {
    expect(moverState({ currentMinor: 3000, previousMinor: 2000, deltaMinor: 1000 })).toBe('up')
  })
  it('is "down" when spending fell', () => {
    expect(moverState({ currentMinor: 2000, previousMinor: 2500, deltaMinor: -500 })).toBe('down')
  })
  it('is "flat" when unchanged', () => {
    expect(moverState({ currentMinor: 500, previousMinor: 500, deltaMinor: 0 })).toBe('flat')
  })
})

describe('moverPercent', () => {
  it('returns the signed percentage vs the previous value', () => {
    expect(moverPercent({ deltaMinor: 1000, previousMinor: 2000 })).toBe(50)
  })
  it('returns null when there is no previous base', () => {
    expect(moverPercent({ deltaMinor: 300, previousMinor: 0 })).toBeNull()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- movers`
Expected: FAIL — `./movers` module does not exist.

- [ ] **Step 3: Write the helpers**

Create `frontend/src/features/trends/movers.ts`:

```ts
import type { CategoryMover } from '@/api'

export type MoverState = 'new' | 'gone' | 'up' | 'down' | 'flat'

type MoverAmounts = Pick<CategoryMover, 'currentMinor' | 'previousMinor' | 'deltaMinor'>

/** Classifies a mover for arrow/colour/label at the display edge. */
export function moverState(m: MoverAmounts): MoverState {
  if (m.previousMinor === 0 && m.currentMinor > 0) return 'new'
  if (m.currentMinor === 0 && m.previousMinor > 0) return 'gone'
  if (m.deltaMinor > 0) return 'up'
  if (m.deltaMinor < 0) return 'down'
  return 'flat'
}

/** Signed % change vs the previous value, or null when there is no base to divide by. */
export function moverPercent(m: Pick<CategoryMover, 'deltaMinor' | 'previousMinor'>): number | null {
  return m.previousMinor !== 0 ? (m.deltaMinor / m.previousMinor) * 100 : null
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- movers`
Expected: PASS (7 assertions green).

---

## Task 5: Frontend — strip + movers components + i18n strings

**Files:**
- Modify: `frontend/src/lib/i18n.ts`
- Create: `frontend/src/features/trends/ComparisonStrip.tsx`
- Create: `frontend/src/features/trends/CategoryMovers.tsx`

- [ ] **Step 1: Add the i18n strings (en + pl)**

In `frontend/src/lib/i18n.ts`, replace the English `trends` block (currently):

```ts
      trends: {
        title: 'Trends', total: 'Total', category: 'By category', month: 'Monthly', week: 'Weekly',
        income: 'Income', expense: 'Expense', net: 'Running net', empty: 'No activity in this period yet.',
      },
```

with:

```ts
      trends: {
        title: 'Trends', total: 'Total', category: 'By category', month: 'Monthly', week: 'Weekly',
        income: 'Income', expense: 'Expense', net: 'Running net', empty: 'No activity in this period yet.',
        compare: 'Compare to previous period', vsPrev: 'vs prev', expenseTotal: 'Expenses', netTotal: 'Net',
        moversTitle: 'Category movers · expenses vs previous', moverNew: 'new', moverGone: 'gone',
        noMovers: 'No comparable spending in either period.',
        highlight: 'Biggest mover: {{name}} ({{change}}) vs the previous period.',
      },
```

In the Polish bundle, replace:

```ts
      trends: {
        title: 'Trendy', total: 'Razem', category: 'Wg kategorii', month: 'Miesięcznie', week: 'Tygodniowo',
        income: 'Przychody', expense: 'Wydatki', net: 'Saldo skumulowane', empty: 'Brak aktywności w tym okresie.',
      },
```

with:

```ts
      trends: {
        title: 'Trendy', total: 'Razem', category: 'Wg kategorii', month: 'Miesięcznie', week: 'Tygodniowo',
        income: 'Przychody', expense: 'Wydatki', net: 'Saldo skumulowane', empty: 'Brak aktywności w tym okresie.',
        compare: 'Porównaj z poprzednim okresem', vsPrev: 'wzgl. poprz.', expenseTotal: 'Wydatki', netTotal: 'Saldo',
        moversTitle: 'Zmiany kategorii · wydatki wzgl. poprzedniego', moverNew: 'nowa', moverGone: 'brak',
        noMovers: 'Brak porównywalnych wydatków w obu okresach.',
        highlight: 'Największa zmiana: {{name}} ({{change}}) wzgl. poprzedniego okresu.',
      },
```

- [ ] **Step 2: Create the summary strip**

Create `frontend/src/features/trends/ComparisonStrip.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import type { TrendComparison } from '@/api'
import { StatCard } from '@/components/StatCard'

/** Income / expenses / net for the selected range, each with Δ vs the previous equal-length period. */
export function ComparisonStrip({ data, currency }: { data: TrendComparison; currency: string }) {
  const { t } = useTranslation()
  const label = t('trends.vsPrev')
  return (
    <div className="mb-6 grid gap-4 sm:grid-cols-3">
      <StatCard
        label={t('trends.income')}
        minor={data.current.incomeMinor}
        currency={currency}
        tone="income"
        previousMinor={data.previous.incomeMinor}
        goodWhenUp
        compareLabel={label}
      />
      <StatCard
        label={t('trends.expenseTotal')}
        minor={data.current.expenseMinor}
        currency={currency}
        tone="expense"
        previousMinor={data.previous.expenseMinor}
        goodWhenUp={false}
        compareLabel={label}
      />
      <StatCard
        label={t('trends.netTotal')}
        minor={data.current.netMinor}
        currency={currency}
        tone="net"
        previousMinor={data.previous.netMinor}
        goodWhenUp
        compareLabel={label}
      />
    </div>
  )
}
```

- [ ] **Step 3: Create the movers panel**

Create `frontend/src/features/trends/CategoryMovers.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import type { CategoryMover, TrendComparison } from '@/api'
import { Card } from '@/components/primitives'
import { useFormatMoney } from '@/components/Money'
import { moverPercent, moverState } from './movers'

/** Highlight line + ranked list of expense-category movers (biggest absolute change first). */
export function CategoryMovers({ data, currency }: { data: TrendComparison; currency: string }) {
  const { t } = useTranslation()
  const formatMoney = useFormatMoney()

  if (data.movers.length === 0) {
    return (
      <Card className="mt-6 p-5">
        <p className="text-sm text-fg-soft">{t('trends.noMovers')}</p>
      </Card>
    )
  }

  const signed = (minor: number) =>
    `${minor > 0 ? '+' : minor < 0 ? '−' : ''}${formatMoney(Math.abs(minor), currency)}`

  const top = data.movers[0]
  const highlight = t('trends.highlight', { name: top.name, change: signed(top.deltaMinor) })

  const changeText = (m: CategoryMover) => {
    const state = moverState(m)
    if (state === 'new') return t('trends.moverNew')
    if (state === 'gone') return t('trends.moverGone')
    const pct = moverPercent(m)
    return pct !== null ? `${Math.abs(pct).toFixed(0)}%` : signed(m.deltaMinor)
  }

  return (
    <Card className="mt-6 overflow-hidden p-0">
      <p className="border-b border-border-subtle bg-surface-2/40 px-4 py-2 text-xs font-medium uppercase tracking-wide text-fg-soft">
        {t('trends.moversTitle')}
      </p>
      <p className="px-4 py-3 text-sm text-fg">{highlight}</p>
      <ul className="divide-y divide-border-subtle">
        {data.movers.map((m) => {
          const state = moverState(m)
          const worse = state === 'up' || state === 'new'
          const better = state === 'down' || state === 'gone'
          const tone = worse ? 'text-negative' : better ? 'text-positive' : 'text-fg-subtle'
          const arrow = worse ? '▲ ' : better ? '▼ ' : ''
          return (
            <li key={m.categoryId ?? 'uncategorized'} className="flex items-center gap-3 px-4 py-2.5">
              <span className="h-2.5 w-2.5 flex-none rounded-sm" style={{ backgroundColor: m.color }} />
              <span className="flex-1 text-sm text-fg">{m.name}</span>
              <span className="w-24 text-right text-sm tabular-nums text-fg-soft">
                {formatMoney(m.currentMinor, currency)}
              </span>
              <span className={`w-28 text-right text-sm font-medium tabular-nums ${tone}`}>
                {arrow}
                {changeText(m)}
              </span>
            </li>
          )
        })}
      </ul>
    </Card>
  )
}
```

- [ ] **Step 4: Typecheck + unit tests**

Run: `npx tsc --noEmit && npm test`
Expected: PASS. (No commit yet — committed with the wiring in Task 6.)

---

## Task 6: Frontend — wire the toggle into `TrendsPage`, verify, commit

**Files:**
- Modify: `frontend/src/features/trends/TrendsPage.tsx`

- [ ] **Step 1: Import the hook and the two components**

In `frontend/src/features/trends/TrendsPage.tsx`, update the reports-hooks import and add the component imports:

```tsx
import { useCashflow, useCategoryTrend, useTrendComparison } from '@/features/reports/hooks'
import { ComparisonStrip } from '@/features/trends/ComparisonStrip'
import { CategoryMovers } from '@/features/trends/CategoryMovers'
```

- [ ] **Step 2: Add toggle state and the query**

Immediately after the existing `const [view, setView] = useState<...>('total')` line, add:

```tsx
  const [compare, setCompare] = useState(false)
```

After the existing `const catTrend = useCategoryTrend(...)` line, add:

```tsx
  const cmp = useTrendComparison(range.from, range.to, compare)
```

- [ ] **Step 3: Add the toggle button to the presets row**

In the presets row `div` (the `<div className="mb-6 flex flex-wrap items-center gap-2">` block), add a right-aligned toggle as the last child, after the `{preset === 'custom' && (...)}` block and before the closing `</div>`:

```tsx
        <div className="ml-auto">
          <Button
            variant={compare ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => setCompare((c) => !c)}
            aria-pressed={compare}
          >
            {t('trends.compare')}
          </Button>
        </div>
```

- [ ] **Step 4: Render the strip above the chart and the movers below it**

Wrap the existing chart area so the comparison blocks bracket it. Directly **before** the existing `{active.isLoading ? (...)}` chart expression, add:

```tsx
      {compare && cmp.data && <ComparisonStrip data={cmp.data} currency={cmp.data.currency} />}
```

Directly **after** the closing of that chart expression (after the final `)}` that ends the `{active.isLoading ? ... : ...}` block, still inside the `<>...</>` fragment), add:

```tsx
      {compare && cmp.data && <CategoryMovers data={cmp.data} currency={cmp.data.currency} />}
```

- [ ] **Step 5: Full frontend verification**

Run: `npm run lint && npx tsc --noEmit && npm test && npm run build`
Expected: all green (lint clean, no type errors, 19 unit tests pass — the original 17 plus the 2 new `movers` describe-blocks, build succeeds).

- [ ] **Step 6: One-off Playwright smoke check (temp spec — do not commit)**

Bring the stack up (see the spec's referenced run recipe / `HANDOFF.md` §5), then create a throwaway spec `frontend/e2e/_tmp-trends-compare.spec.ts` that: registers a user, creates an account + two expenses dated in the current and previous month, opens `/trends`, clicks the **Compare to previous period** button, and asserts the summary strip and movers panel appear (e.g. `await expect(page.getByText(/vs prev|wzgl\. poprz\./)).toBeVisible()`). Run `npm run e2e -- _tmp-trends-compare`, confirm green, then **delete the temp spec**:

```bash
rm frontend/e2e/_tmp-trends-compare.spec.ts
```

Confirm the committed E2E set is still just `core-loop.spec.ts` + `budgets.spec.ts`:

```bash
ls frontend/e2e
```

- [ ] **Step 7: Commit the frontend feature**

```bash
git add frontend/src/api/types.ts frontend/src/features/reports/hooks.ts \
  frontend/src/features/trends frontend/src/lib/i18n.ts
git commit -m "feat(frontend): Trends period-comparison (strip + category movers)

Opt-in 'Compare to previous period' toggle on the Trends tab: a summary strip
(income/expenses/net Δ vs the previous equal-length period) above the chart and a
ranked expense-category movers panel below it. Additive — off by default.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Stop at the phase boundary

- [ ] **Step 1: Confirm the working tree is clean and both builds are green**

Run (backend): `cd backend && ./gradlew build`
Run (frontend): `cd frontend && npm run lint && npm test && npm run build`
Run: `git status` — expect a clean tree with the three new commits (feat backend, refactor frontend, feat frontend) on `phase-7-trends-comparison`.

- [ ] **Step 2: Hand back to the user**

Do **not** push. Summarise what shipped and ask the user to test in-app (toggle on the Trends tab, verify the strip + movers against known data, check dark mode and PL/EN), per the phase-gated workflow. Update `HANDOFF.md` §17 to mark the Trends comparison delivered. Push only when the user asks.

---

## Self-Review

**Spec coverage:**
- §2 equal-length previous period → Task 1 Step 4 (`days`/`prevTo`/`prevFrom`), asserted in Task 1 Step 1 (`previous.from/to`). ✓
- §3 UI: single off-by-default toggle → Task 6 Steps 2–3; strip above chart, movers full-width below → Task 6 Step 4; income/expenses/net + Δ → Task 5 Step 2; movers expense-only, rolled to parent, sorted by abs change, new/gone/uncategorized → Task 1 (backend) + Task 5 Step 3; highlight line → Task 5 Step 3; empty state → Task 5 Step 3 (`noMovers`); token-based styling → Task 5 Step 3 (uses `border-border-subtle`, `bg-surface-2`, `text-fg*` tokens). ✓
- §4 endpoint/DTO/service/no-migration → Task 1. ✓
- §5 hook/types/components/i18n → Tasks 3, 5, 6. ✓
- §6 backend fixed-fixture tests + frontend Vitest + one-off Playwright → Task 1 Step 1, Task 4, Task 6 Step 6. ✓
- §7 build order, separate commits, stop at boundary, no push → Tasks 1/2/6 commits + Task 7. ✓
- §8 out of scope (no overlay, no income movers, no migration) → honored; nothing in the plan adds them. ✓

**Placeholder scan:** No TBD/TODO; every code step shows complete code; the one-off Playwright spec (Task 6 Step 6) is intentionally described rather than committed, per the standing "verification is one-off" rule.

**Type consistency:** `TrendComparisonResponse`/`CategoryMover` (Java) mirror `TrendComparison`/`CategoryMover` (TS) field-for-field (`currency`, `current`, `previous`, `delta`, `movers`; mover: `categoryId`, `name`, `color`, `currentMinor`, `previousMinor`, `deltaMinor`). `useTrendComparison(from, to, enabled)` matches its call in Task 6 Step 2. `moverState`/`moverPercent` signatures match their test (Task 4) and use in `CategoryMovers` (Task 5). `StatCard` props used by `ComparisonStrip` match the extracted component. i18n keys referenced (`trends.compare/vsPrev/income/expenseTotal/netTotal/moversTitle/moverNew/moverGone/noMovers/highlight`) are all added in Task 5 Step 1.
