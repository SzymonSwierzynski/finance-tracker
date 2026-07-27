# Budget Rollover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give per-category budgets an opt-in `rollover` flag that carries unspent budget forward month-over-month (floored envelope model), leaving strict budgets unchanged.

**Architecture:** Add a `rollover` boolean to `budgets` (V13). When on, `BudgetService.list` folds prior-month spend into a `carriedIn` for the requested month via a pure `RolloverCalculator`, reusing the existing `sumByPeriodAndCategory` query for historical per-(month, category) spend. `available = limit + carriedIn`; `remaining`/`over` compare against `available` (identical to today when `carriedIn = 0`).

**Tech Stack:** Spring Boot 3.5 / Java 21 / JPA / Flyway / Postgres 16 (backend); React 19 + TS + Tailwind v4 + RHF/Zod + TanStack Query (frontend). Build with **Java 21** (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`), absolute paths.

**Spec:** `docs/superpowers/specs/2026-07-26-budget-rollover-design.md`

---

## Standing rules for the executor (project §17)

- **Commit only when the user asks; push only when the user asks.** The `Commit` steps below are real, but **pause for the user's go-ahead** before running each (backend commit, then frontend commit — separate commits).
- **Backend first, then frontend.** Keep both green: `cd backend && ./gradlew build` and `cd frontend && npm run lint && npm test && npm run build`.
- **Stop at the phase boundary** (after Task 5) for the user to test in-app.
- Local-only docs (`HANDOFF.md`, root `CLAUDE.md`, `review.md`) are git-ignored — update on disk, never commit.

---

## Task 0: Branch

- [ ] **Step 1: Create the feature branch off `main`**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
git checkout main && git switch -c backlog-budget-rollover
```
Expected: `Switched to a new branch 'backlog-budget-rollover'`. Working tree clean.

---

## Task 1: Persist the `rollover` flag (schema + entity + DTO plumbing)

Additive only — no carry logic yet (`carriedInMinor` is hard-wired to 0). Existing behavior is unchanged; existing `BudgetIsolationTest` stays green.

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__budgets_rollover.sql`
- Modify: `backend/src/main/java/com/financetracker/budget/Budget.java`
- Modify: `backend/src/main/java/com/financetracker/budget/dto/CreateBudgetRequest.java`
- Modify: `backend/src/main/java/com/financetracker/budget/dto/UpdateBudgetRequest.java`
- Modify: `backend/src/main/java/com/financetracker/budget/dto/BudgetResponse.java`
- Modify: `backend/src/main/java/com/financetracker/budget/dto/BudgetsResponse.java`
- Modify: `backend/src/main/java/com/financetracker/budget/BudgetService.java`
- Test: `backend/src/test/java/com/financetracker/budget/BudgetIsolationTest.java`

- [ ] **Step 1: Write the failing test** (append a method inside `BudgetIsolationTest`, before the closing brace / private helpers)

```java
  @Test
  void persistsAndEchoesTheRolloverFlag() throws Exception {
    RegisteredUser user = register("budget-rollover-flag@example.com", "password123");
    clearCategories(user);
    long food = createCategory(user, "Food", "expense");

    // Explicit rollover=true on create is stored and echoed on the create response…
    long id =
        id(
            mockMvc
                .perform(
                    post("/api/v1/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"categoryId\":" + food + ",\"amountMinor\":50000,\"rollover\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rollover").value(true))
                .andReturn());

    // …and on the monthly progress list, with carriedInMinor present (0 for now).
    mockMvc
        .perform(get("/api/v1/budgets").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].rollover").value(true))
        .andExpect(jsonPath("$.items[0].carriedInMinor").value(0));

    // Omitting rollover defaults to false (a new category so no duplicate-budget conflict).
    long rent = createCategory(user, "Rent", "expense");
    mockMvc
        .perform(
            post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + rent + ",\"amountMinor\":100000}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rollover").value(false));

    // Update can toggle rollover off (send the current version).
    mockMvc
        .perform(
            patch("/api/v1/budgets/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":50000,\"version\":0,\"rollover\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rollover").value(false));
  }
```

- [ ] **Step 2: Run it to confirm it fails**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.budget.BudgetIsolationTest.persistsAndEchoesTheRolloverFlag'
```
Expected: FAIL — `$.rollover`/`$.items[0].carriedInMinor` are missing (`No value at JSON path`).

- [ ] **Step 3: Add the V13 migration**

Create `backend/src/main/resources/db/migration/V13__budgets_rollover.sql`:
```sql
-- Backlog A: opt-in budget rollover. When true, a month's available amount is the fixed limit plus
-- the accumulated unspent balance from prior months (floored at zero — no carried debt). Existing
-- rows default to false, preserving strict month-to-month behavior.

ALTER TABLE budgets ADD COLUMN rollover BOOLEAN NOT NULL DEFAULT FALSE;
```

- [ ] **Step 4: Add the entity field**

In `backend/src/main/java/com/financetracker/budget/Budget.java`, add after the `amountMinor` field (Lombok generates `isRollover()` / `setRollover(boolean)`):
```java
  @Column(name = "rollover", nullable = false)
  private boolean rollover;
```

- [ ] **Step 5: Add `rollover` to the request/response DTOs**

`CreateBudgetRequest.java` — add the component (a missing JSON field deserializes to `false` for a primitive boolean; no validation annotation):
```java
public record CreateBudgetRequest(
    @NotNull Long categoryId, @Positive long amountMinor, boolean rollover) {}
```

`UpdateBudgetRequest.java`:
```java
public record UpdateBudgetRequest(@Positive long amountMinor, long version, boolean rollover) {}
```

`BudgetResponse.java`:
```java
public record BudgetResponse(
    long id, long categoryId, long amountMinor, long version, boolean rollover) {}
```

`BudgetsResponse.java` — add two fields to `BudgetProgress` (keep the record component order; `carriedInMinor` is the accumulated carry for the month, `rollover` echoes the flag):
```java
  public record BudgetProgress(
      long id,
      long categoryId,
      String categoryName,
      String color,
      long amountMinor,
      long spentMinor,
      long remainingMinor,
      boolean over,
      long version,
      boolean rollover,
      long carriedInMinor) {}
```

- [ ] **Step 6: Plumb the flag through `BudgetService`** (no fold yet — `carriedInMinor` is 0)

In `BudgetService.java`:

Set the flag on create — in `create(...)`, after `budget.setAmountMinor(request.amountMinor());`:
```java
    budget.setRollover(request.rollover());
```

Set it on update — in `update(...)`, after `budget.setAmountMinor(request.amountMinor());`:
```java
    budget.setRollover(request.rollover());
```

Update `toProgress` to pass the two new fields (carry 0 for now):
```java
  private BudgetProgress toProgress(Budget b, Category category, Map<Long, Long> spentByCategory) {
    long spent = spentByCategory.getOrDefault(b.getCategoryId(), 0L);
    long carriedIn = 0L;
    long available = b.getAmountMinor() + carriedIn;
    return new BudgetProgress(
        b.getId(),
        b.getCategoryId(),
        category == null ? "" : category.getName(),
        category == null ? "" : category.getColor(),
        b.getAmountMinor(),
        spent,
        available - spent,
        spent > available,
        b.getVersion(),
        b.isRollover(),
        carriedIn);
  }
```

Update `toResponse` to include the flag:
```java
  private static BudgetResponse toResponse(Budget b) {
    return new BudgetResponse(
        b.getId(), b.getCategoryId(), b.getAmountMinor(), b.getVersion(), b.isRollover());
  }
```

- [ ] **Step 7: Run the test to confirm it passes, then the full build**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.budget.BudgetIsolationTest'
```
Expected: PASS (all `BudgetIsolationTest` methods, incl. the new one).

Then the full gate:
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build
```
Expected: BUILD SUCCESSFUL (Spotless + all tests + JaCoCo ≥ 0.85).

- [ ] **Step 8: Commit** (only after the user's go-ahead)

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
git add backend/src/main/resources/db/migration/V13__budgets_rollover.sql \
        backend/src/main/java/com/financetracker/budget/ \
        backend/src/test/java/com/financetracker/budget/BudgetIsolationTest.java
git commit -m "feat(backend): budget rollover — persist opt-in flag (V13)"
```

---

## Task 2: Pure `RolloverCalculator` (the floored fold)

A dependency-free function so the exact §2 table is unit-tested deterministically (no Spring, no DB, no "now").

**Files:**
- Create: `backend/src/main/java/com/financetracker/budget/RolloverCalculator.java`
- Test: `backend/src/test/java/com/financetracker/budget/RolloverCalculatorTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/financetracker/budget/RolloverCalculatorTest.java`:
```java
package com.financetracker.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The floored, compounding carry — the §2 design table, asserted deterministically. */
class RolloverCalculatorTest {

  private static final YearMonth JAN = YearMonth.of(2026, 1);
  private static final long LIMIT = 50000; // 500.00

  // Spend per month (minor): Jan 400, Feb 550, Mar 620, Apr 300.
  private static final Map<YearMonth, Long> SPEND =
      Map.of(
          JAN, 40000L,
          JAN.plusMonths(1), 55000L,
          JAN.plusMonths(2), 62000L,
          JAN.plusMonths(3), 30000L);

  @Test
  void foldsUnspentBudgetForwardWithAFloor() {
    // carriedIn(m) folds months [creation, m). Creation = JAN.
    assertThat(RolloverCalculator.carriedIn(JAN, JAN, LIMIT, SPEND)).isZero(); // creation month
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(1), LIMIT, SPEND)).isEqualTo(10000);
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(2), LIMIT, SPEND)).isEqualTo(5000);
    // Mar overspends (620 > 550 available) → carry floored at 0, not -70.
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(3), LIMIT, SPEND)).isZero();
    // Apr underspends from a fresh 500 → 200 carried into May.
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(4), LIMIT, SPEND)).isEqualTo(20000);
  }

  @Test
  void targetAtOrBeforeCreationCarriesZero() {
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.minusMonths(1), LIMIT, SPEND)).isZero();
  }

  @Test
  void monthsWithNoSpendAccrueTheFullLimit() {
    assertThat(RolloverCalculator.carriedIn(JAN, JAN.plusMonths(3), LIMIT, Map.of()))
        .isEqualTo(150000); // three empty months × 500.00
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.budget.RolloverCalculatorTest'
```
Expected: FAIL — `RolloverCalculator` does not exist (compile error).

- [ ] **Step 3: Implement the calculator**

Create `backend/src/main/java/com/financetracker/budget/RolloverCalculator.java`:
```java
package com.financetracker.budget;

import java.time.YearMonth;
import java.util.Map;

/**
 * The floored, compounding budget carry (design §2). {@code carriedIn(targetMonth)} folds every month
 * in {@code [creationMonth, targetMonth)}: each step, the month's available amount (limit + carry so
 * far) minus its spend rolls forward, floored at zero so overspending never creates carried debt.
 * Months absent from {@code spentByMonth} count as zero spend. Pure — no Spring, no persistence.
 */
public final class RolloverCalculator {

  private RolloverCalculator() {}

  public static long carriedIn(
      YearMonth creationMonth, YearMonth targetMonth, long limit, Map<YearMonth, Long> spentByMonth) {
    long carry = 0L;
    for (YearMonth m = creationMonth; m.isBefore(targetMonth); m = m.plusMonths(1)) {
      long spent = spentByMonth.getOrDefault(m, 0L);
      carry = Math.max(0L, limit + carry - spent);
    }
    return carry;
  }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.budget.RolloverCalculatorTest'
```
Expected: PASS (3 tests).

- [ ] **Step 5: Commit** (after the user's go-ahead — can be batched with Task 3's commit)

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
git add backend/src/main/java/com/financetracker/budget/RolloverCalculator.java \
        backend/src/test/java/com/financetracker/budget/RolloverCalculatorTest.java
git commit -m "feat(backend): budget rollover — floored carry calculator"
```

---

## Task 3: Wire the fold into `BudgetService.list`

Compute a real `carriedIn` for rollover budgets by folding historical per-(month, category) spend; off budgets keep `carriedIn = 0` and never trigger the historical query.

**Files:**
- Modify: `backend/src/main/java/com/financetracker/budget/BudgetService.java`
- Test: `backend/src/test/java/com/financetracker/budget/BudgetRolloverTest.java`

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/financetracker/budget/BudgetRolloverTest.java`:
```java
package com.financetracker.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Budget rollover end-to-end: the floored envelope carry (design §2), off-budget parity, and
 * subcategory roll-up into a parent's carry. The anchor is the freshly-created budget's creation
 * month (≈ now), so the fixture uses now-relative months — a fixed past month would sit before
 * creation and never fold. Cross-user isolation is already covered by {@link BudgetIsolationTest}.
 */
class BudgetRolloverTest extends AbstractIntegrationTest {

  @Test
  void carriesUnspentBudgetForwardWithFloor() throws Exception {
    RegisteredUser user = register("rollover-carry@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    createRolloverBudget(user, food, 50000); // limit 500.00, rollover on, created this month

    YearMonth base = YearMonth.now(ZoneOffset.UTC); // = creation month
    createExpense(user, account, base.atDay(5).toString(), 40000, food); // base:   400
    createExpense(user, account, base.plusMonths(1).atDay(5).toString(), 55000, food); // +1: 550
    createExpense(user, account, base.plusMonths(2).atDay(5).toString(), 62000, food); // +2: 620
    createExpense(user, account, base.plusMonths(3).atDay(5).toString(), 30000, food); // +3: 300

    // month, carriedIn, spent, remaining, over  (amount is always 50000; available = 50000+carriedIn)
    assertMonth(user, base, 0, 40000, 10000, false); // avail 500, spent 400
    assertMonth(user, base.plusMonths(1), 10000, 55000, 5000, false); // avail 600, spent 550
    assertMonth(user, base.plusMonths(2), 5000, 62000, -7000, true); // avail 550, spent 620 → over
    assertMonth(user, base.plusMonths(3), 0, 30000, 20000, false); // carry floored → avail 500
  }

  @Test
  void rolloverOffIgnoresPriorMonths() throws Exception {
    RegisteredUser user = register("rollover-off@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    createBudget(user, food, 50000); // rollover defaults false

    YearMonth base = YearMonth.now(ZoneOffset.UTC);
    createExpense(user, account, base.atDay(5).toString(), 10000, food); // underspend this month

    // Next month starts fresh — no carry, exactly as before.
    mockMvc
        .perform(
            get("/api/v1/budgets?month=" + base.plusMonths(1))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].rollover").value(false))
        .andExpect(jsonPath("$.items[0].carriedInMinor").value(0))
        .andExpect(jsonPath("$.items[0].amountMinor").value(50000))
        .andExpect(jsonPath("$.items[0].spentMinor").value(0))
        .andExpect(jsonPath("$.items[0].remainingMinor").value(50000))
        .andExpect(jsonPath("$.items[0].over").value(false));
  }

  @Test
  void foldsSubcategorySpendIntoParentCarry() throws Exception {
    RegisteredUser user = register("rollover-subcat@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    long restaurants = createSubcategory(user, "Restaurants", "expense", food);
    createRolloverBudget(user, food, 50000);

    YearMonth base = YearMonth.now(ZoneOffset.UTC);
    createExpense(user, account, base.atDay(5).toString(), 30000, restaurants); // rolls to parent

    // Parent carry = 50000 - 30000 = 20000 → next month available 70000.
    assertMonth(user, base.plusMonths(1), 20000, 0, 70000, false);
  }

  private void assertMonth(
      RegisteredUser user, YearMonth month, long carriedIn, long spent, long remaining, boolean over)
      throws Exception {
    mockMvc
        .perform(
            get("/api/v1/budgets?month=" + month).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].rollover").value(true))
        .andExpect(jsonPath("$.items[0].amountMinor").value(50000))
        .andExpect(jsonPath("$.items[0].carriedInMinor").value(carriedIn))
        .andExpect(jsonPath("$.items[0].spentMinor").value(spent))
        .andExpect(jsonPath("$.items[0].remainingMinor").value(remaining))
        .andExpect(jsonPath("$.items[0].over").value(over));
  }

  private long createRolloverBudget(RegisteredUser user, long categoryId, long amountMinor)
      throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/budgets")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"categoryId\":"
                            + categoryId
                            + ",\"amountMinor\":"
                            + amountMinor
                            + ",\"rollover\":true}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long createBudget(RegisteredUser user, long categoryId, long amountMinor)
      throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/budgets")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"categoryId\":" + categoryId + ",\"amountMinor\":" + amountMinor + "}"))
            .andExpect(status().isCreated())
            .andReturn());
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

  private long createCategory(RegisteredUser user, String name, String kind) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long createSubcategory(RegisteredUser user, String name, String kind, long parentId)
      throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\""
                            + name
                            + "\",\"kind\":\""
                            + kind
                            + "\",\"parentId\":"
                            + parentId
                            + "}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void createExpense(
      RegisteredUser user, long account, String date, long amount, long categoryId)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\""
                        + date
                        + "\",\"amountMinor\":"
                        + amount
                        + ",\"type\":\"expense\",\"accountId\":"
                        + account
                        + ",\"categoryId\":"
                        + categoryId
                        + "}"))
        .andExpect(status().isCreated());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.budget.BudgetRolloverTest'
```
Expected: FAIL — `carriedInMinor`/`remainingMinor` assert against the still-zero carry (e.g. `base+1` expects `carriedInMinor 10000` but gets `0`).

- [ ] **Step 3: Implement the fold in `BudgetService`**

In `BudgetService.java`:

Add imports (near the other `java.time` / `java.util` imports):
```java
import com.financetracker.transaction.TransactionRepository.PeriodCategorySumRow;
import java.time.ZoneOffset;
```

Replace the `list(...)` method body so it builds the historical carry map when needed and passes it to `toProgress`:
```java
  @Transactional(readOnly = true)
  public BudgetsResponse list(long userId, String month) {
    YearMonth ym = parseMonth(month);
    List<Budget> budgets = budgetRepository.findByUserId(userId);
    Map<Long, Category> byId =
        categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
            .collect(Collectors.toMap(Category::getId, Function.identity()));
    Map<Long, Long> spentByCategory = rollUpExpenses(userId, ym, byId);
    Map<Long, Map<YearMonth, Long>> historyByCategory = historicalSpend(userId, ym, budgets, byId);

    List<BudgetProgress> items =
        budgets.stream()
            .map(b -> toProgress(b, byId.get(b.getCategoryId()), spentByCategory, historyByCategory, ym))
            .sorted(
                Comparator.comparing(BudgetProgress::categoryName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    return new BudgetsResponse(ym.toString(), settingsService.reportingCurrency(userId), items);
  }

  /**
   * Per-(month, category) base-currency expense spend for every month a rollover budget must fold
   * over — i.e. from the earliest rollover-budget creation month up to the month before {@code ym}.
   * Empty (no rollover budgets) so off budgets pay nothing extra. Subcategory spend is folded into
   * the parent, mirroring {@link #rollUpExpenses}. Keyed category id → (month → base minor).
   */
  private Map<Long, Map<YearMonth, Long>> historicalSpend(
      long userId, YearMonth ym, List<Budget> budgets, Map<Long, Category> byId) {
    YearMonth earliest =
        budgets.stream()
            .filter(Budget::isRollover)
            .map(this::creationMonth)
            .min(Comparator.naturalOrder())
            .orElse(null);
    Map<Long, Map<YearMonth, Long>> history = new HashMap<>();
    if (earliest == null || !earliest.isBefore(ym)) {
      return history; // no rollover budgets, or nothing precedes the requested month
    }
    for (PeriodCategorySumRow row :
        transactionRepository.sumByPeriodAndCategory(
            userId,
            earliest.atDay(1),
            ym.minusMonths(1).atEndOfMonth(),
            "YYYY-MM",
            CategoryKind.EXPENSE.value())) {
      Category cat = row.getCategoryId() == null ? null : byId.get(row.getCategoryId());
      if (cat == null) {
        continue;
      }
      YearMonth m = YearMonth.parse(row.getPeriod());
      long base = baseMinorOf(row.getBaseMinor());
      accrue(history, cat.getId(), m, base);
      if (cat.getParentId() != null) {
        accrue(history, cat.getParentId(), m, base);
      }
    }
    return history;
  }

  private static void accrue(
      Map<Long, Map<YearMonth, Long>> history, long categoryId, YearMonth month, long base) {
    history.computeIfAbsent(categoryId, k -> new HashMap<>()).merge(month, base, Long::sum);
  }

  private YearMonth creationMonth(Budget b) {
    return YearMonth.from(b.getCreatedAt().atZone(ZoneOffset.UTC));
  }
```

Replace `toProgress` to compute the real carry for rollover budgets:
```java
  private BudgetProgress toProgress(
      Budget b,
      Category category,
      Map<Long, Long> spentByCategory,
      Map<Long, Map<YearMonth, Long>> historyByCategory,
      YearMonth ym) {
    long spent = spentByCategory.getOrDefault(b.getCategoryId(), 0L);
    long carriedIn =
        b.isRollover()
            ? RolloverCalculator.carriedIn(
                creationMonth(b),
                ym,
                b.getAmountMinor(),
                historyByCategory.getOrDefault(b.getCategoryId(), Map.of()))
            : 0L;
    long available = b.getAmountMinor() + carriedIn;
    return new BudgetProgress(
        b.getId(),
        b.getCategoryId(),
        category == null ? "" : category.getName(),
        category == null ? "" : category.getColor(),
        b.getAmountMinor(),
        spent,
        available - spent,
        spent > available,
        b.getVersion(),
        b.isRollover(),
        carriedIn);
  }
```

(Delete the interim `toProgress` from Task 1 Step 6 — it's fully replaced by this signature.)

- [ ] **Step 4: Run the test to confirm it passes**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.budget.BudgetRolloverTest'
```
Expected: PASS (3 tests).

- [ ] **Step 5: Run the full backend gate**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build
```
Expected: BUILD SUCCESSFUL (Spotless + all tests + JaCoCo ≥ 0.85). If Spotless reports formatting, run `./gradlew spotlessApply` and rebuild.

- [ ] **Step 6: Commit** (after the user's go-ahead)

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
git add backend/src/main/java/com/financetracker/budget/BudgetService.java \
        backend/src/test/java/com/financetracker/budget/BudgetRolloverTest.java \
        backend/src/main/java/com/financetracker/budget/RolloverCalculator.java \
        backend/src/test/java/com/financetracker/budget/RolloverCalculatorTest.java
git commit -m "feat(backend): budget rollover — fold prior-month carry into progress"
```
(If Task 2 was already committed separately, drop the calculator files from this `git add`.)

---

## Task 4: Frontend — toggle + carried display

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/lib/i18n.ts`
- Modify: `frontend/src/features/budgets/BudgetForm.tsx`
- Modify: `frontend/src/features/budgets/BudgetsPage.tsx`

- [ ] **Step 1: Extend the budget types** (`frontend/src/api/types.ts`, lines ~375-403)

Set the four interfaces to:
```ts
export interface CreateBudgetRequest {
  categoryId: number
  amountMinor: number
  rollover: boolean
}

export interface UpdateBudgetRequest {
  amountMinor: number
  version: number
  rollover: boolean
}

export interface BudgetResponse {
  id: number
  categoryId: number
  amountMinor: number
  version: number
  rollover: boolean
}

/** One budget's progress for a month, in base-currency minor units. */
export interface BudgetProgress {
  id: number
  categoryId: number
  categoryName: string
  color: string
  amountMinor: number
  spentMinor: number
  remainingMinor: number
  over: boolean
  version: number
  rollover: boolean
  carriedInMinor: number
}
```
(These are the hand-declared interim types; a later `npm run gen:api` with the backend up will source them from OpenAPI.)

- [ ] **Step 2: Add i18n strings** (`frontend/src/lib/i18n.ts`)

English `budgets` block — replace:
```ts
        over: 'Over', left: 'Left', invalidAmount: 'Enter an amount greater than zero.',
      },
```
with:
```ts
        over: 'Over', left: 'Left', invalidAmount: 'Enter an amount greater than zero.',
        rollover: 'Roll over unused budget', rolloverHelp: 'Unspent amount carries into next month; a heavier month can draw it down, but it never goes below zero.', rolloverBadge: 'Rollover', carried: 'carried',
      },
```

Polish `budgets` block — replace:
```ts
        over: 'Ponad', left: 'Zostało', invalidAmount: 'Podaj kwotę większą od zera.',
      },
```
with:
```ts
        over: 'Ponad', left: 'Zostało', invalidAmount: 'Podaj kwotę większą od zera.',
        rollover: 'Przenoś niewykorzystany budżet', rolloverHelp: 'Niewydana kwota przechodzi na kolejny miesiąc; cięższy miesiąc może ją uszczuplić, ale nie zejdzie poniżej zera.', rolloverBadge: 'Przeniesienie', carried: 'przeniesione',
      },
```

- [ ] **Step 3: Add the checkbox to `BudgetForm.tsx`**

Update the Zod schema and form values, and send `rollover` on submit.

Schema (line ~12):
```ts
const schema = z.object({ categoryId: z.string(), amount: z.string().min(1), rollover: z.boolean() })
```

`useForm` `values` (line ~37-40) — add `rollover`:
```ts
    values: {
      categoryId: edit ? String(edit.categoryId) : '',
      amount: edit ? (edit.amountMinor / 100).toFixed(2) : '',
      rollover: edit ? edit.rollover : false,
    },
```

Submit branches (line ~50-57) — carry the flag through both payloads:
```ts
      if (edit) {
        await update.mutateAsync({
          id: edit.id,
          body: { amountMinor, version: edit.version, rollover: values.rollover },
        })
      } else {
        if (!values.categoryId) {
          toast.error(t('errors.required'))
          return
        }
        await create.mutateAsync({
          categoryId: Number(values.categoryId),
          amountMinor,
          rollover: values.rollover,
        })
      }
```

Add the checkbox after the monthly-limit `Field` (before the closing `</form>`), matching token styling:
```tsx
        <label className="flex items-start gap-2 text-sm text-fg">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 rounded border-border"
            {...register('rollover')}
          />
          <span>
            {t('budgets.rollover')}
            <span className="mt-0.5 block text-xs text-fg-soft">{t('budgets.rolloverHelp')}</span>
          </span>
        </label>
```

- [ ] **Step 4: Show carry on `BudgetsPage.tsx`**

Replace the `pct` computation (lines ~75-76) to base progress on the available amount:
```tsx
            const carried = b.rollover ? b.carriedInMinor : 0
            const available = b.amountMinor + carried
            const pct =
              available > 0 ? Math.min(100, Math.round((b.spentMinor / available) * 100)) : 0
```

Add a rollover badge after the category name — replace:
```tsx
                    <span className="truncate text-sm font-medium text-fg">
                      {b.categoryName}
                    </span>
```
with:
```tsx
                    <span className="truncate text-sm font-medium text-fg">
                      {b.categoryName}
                    </span>
                    {b.rollover && (
                      <span
                        className="shrink-0 rounded-full bg-surface-2 px-2 py-0.5 text-[10px] font-medium text-fg-muted"
                        title={t('budgets.rolloverHelp')}
                      >
                        {t('budgets.rolloverBadge')}
                      </span>
                    )}
```

Show spend against the available amount and the carried note — replace:
```tsx
                  <span>
                    <Money minor={b.spentMinor} currency={currency} /> /{' '}
                    <Money minor={b.amountMinor} currency={currency} />
                  </span>
```
with:
```tsx
                  <span>
                    <Money minor={b.spentMinor} currency={currency} /> /{' '}
                    <Money minor={available} currency={currency} />
                    {carried > 0 && (
                      <span className="ml-1 text-positive">
                        (+<Money minor={carried} currency={currency} /> {t('budgets.carried')})
                      </span>
                    )}
                  </span>
```

- [ ] **Step 5: Run the frontend gate**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/frontend
npm run lint && npm test && npm run build
```
Expected: eslint clean, Vitest green (existing 17), `tsc -b` + vite build succeed.

- [ ] **Step 6: Commit** (after the user's go-ahead)

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
git add frontend/src/api/types.ts frontend/src/lib/i18n.ts frontend/src/features/budgets/
git commit -m "feat(frontend): budget rollover toggle + carried display"
```

---

## Task 5: Boundary — verify + document

- [ ] **Step 1: One-off Playwright smoke (throwaway, not committed)**

With the full stack up (see `HANDOFF.md` §5), write a temp spec that registers, creates a rollover budget, and confirms the checkbox + badge render; run it; then delete it. Do **not** add a committed E2E spec (the lean set stays core-loop + budgets). Screenshot the Budgets page light + dark to eyeball the badge/carried note.

- [ ] **Step 2: Update `HANDOFF.md`** (local-only, never committed)

Record: backlog item **A (budget rollover)** delivered — V13 `rollover` column, floored compounding carry (`RolloverCalculator`), creation-month anchor, `carriedInMinor`/`rollover` on `BudgetProgress`, form toggle + carried display. Note the next backlog item is **B (idempotency keys)** and the next free migration is **V14**. Update the migration table (V13 = budgets_rollover) and the §17 program checklist.

- [ ] **Step 3: Stop for the user to test in-app**

Report green builds (with output) and hand off at the phase boundary. Push only if the user asks.

---

## Self-review notes (author)

- **Spec coverage:** V13 + flag (Task 1) · pure floored fold, model A §2 (Task 2) · anchor = creation month at UTC, reuse `sumByPeriodAndCategory`, off pays nothing extra (Task 3) · form toggle + carried/available display + i18n PL/EN (Task 4) · tests to the grosz, isolation via existing suite, JaCoCo 0.85 (Tasks 1-3) · boundary verify + HANDOFF (Task 5). All §-sections mapped.
- **Back-compat:** new DTO fields are additive; `carriedInMinor = 0` and available = amount for off budgets, so existing `BudgetIsolationTest` assertions hold. Absent `rollover` in JSON → `false` (Jackson primitive default).
- **Type consistency:** `carriedIn(creationMonth, targetMonth, limit, spentByMonth)` and `BudgetProgress(… , boolean rollover, long carriedInMinor)` used identically across Tasks 1-4; `isRollover()` is the Lombok getter for the `boolean rollover` field.
- **Known assumption:** the integration test computes `base = YearMonth.now(UTC)` right after creating the budget; a run crossing a UTC month boundary within those milliseconds is a negligible flake window (same class as the recurring UTC-anchor tests).
