# Soft-delete / Undo (transactions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deleting a transaction moves it to a Trash (soft-delete) instead of destroying it — with an Undo toast, a Trash view (restore / delete forever), and a 30-day auto-purge — while it disappears from every read path (lists, all reports, budgets, balances, export, import dedupe).

**Architecture:** Add a nullable `deleted_at` to `transactions` (V15). Every active-read query gets an explicit `deleted_at IS NULL` (no global `@SQLRestriction`, so Trash/Restore are first-class). Delete sets `deleted_at = now`; restore clears it; a scheduled cleanup hard-deletes rows past retention.

**Tech Stack:** Spring Boot 3.5 / Java 21 / JPA / Flyway / Postgres 16; React 19 + TS. Build with **Java 21** (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`), absolute paths.

**Spec:** `docs/superpowers/specs/2026-07-28-soft-delete-undo-design.md`

---

## Standing rules for the executor (project §17)

- **Commit only when the user asks; push only when the user asks.** Pause for the go-ahead before each `Commit` step (backend then frontend, separate).
- **Backend first, then frontend.** Keep both green (`./gradlew build`; `npm run lint && npm test && npm run build`).
- **Stop at the phase boundary** (after Task 5) for in-app testing.
- Local-only docs (`HANDOFF.md`, `CLAUDE.md`, `review.md`) are git-ignored — update on disk, never commit.

---

## Task 0: Branch

- [ ] **Step 1: Branch off `main`**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
git checkout main && git switch -c backlog-soft-delete-undo
```
Expected: `Switched to a new branch 'backlog-soft-delete-undo'`.

---

## Task 1: Schema + entity + repository read-filters

Additive + behavior-preserving: `deleted_at` is null for all existing rows, so the new `IS NULL` filters are no-ops on current data — **existing tests must stay green**, proving active reads are unbroken.

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__transactions_soft_delete.sql`
- Modify: `backend/src/main/java/com/financetracker/transaction/Transaction.java`
- Modify: `backend/src/main/java/com/financetracker/transaction/TransactionRepository.java`
- Modify: `backend/src/main/java/com/financetracker/transaction/TransactionService.java` (Criteria filter + one call site)
- Modify: `backend/src/main/java/com/financetracker/export/ExportService.java:109`
- Modify: `backend/src/main/java/com/financetracker/category/CategoryService.java:105`
- Modify: `backend/src/main/java/com/financetracker/rule/RuleService.java:105`

- [ ] **Step 1: Migration**

Create `backend/src/main/resources/db/migration/V15__transactions_soft_delete.sql`:
```sql
-- Backlog C: soft-delete for transactions. deleted_at NULL = active; non-null = trashed (invisible to
-- every active read, restorable, purged after retention). Partial indexes keep the hot active path
-- lean and support the trash listing.
ALTER TABLE transactions ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_transactions_active ON transactions (user_id, date) WHERE deleted_at IS NULL;
CREATE INDEX idx_transactions_trash  ON transactions (user_id, deleted_at) WHERE deleted_at IS NOT NULL;
```

- [ ] **Step 2: Entity field**

In `Transaction.java`, add `import java.time.Instant;` (near the other imports) and, after the `recurringId` field:
```java
  @Column(name = "deleted_at")
  private Instant deletedAt;
```

- [ ] **Step 3: Repository — active filters, renamed finders, new trash/restore/purge**

In `TransactionRepository.java`:

Add imports:
```java
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
```

Replace the derived finders (lines ~23-33) with deleted-aware versions + the new trash/restore finders:
```java
  Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(long id, long userId);

  /** A trashed row (for restore / delete-forever). */
  Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNotNull(long id, long userId);

  /** All of the user's active transactions, oldest first — for data export / backup. */
  List<Transaction> findByUserIdAndDeletedAtIsNullOrderByDateAscIdAsc(long userId);

  /** The user's trashed transactions, most-recently-deleted first. */
  Page<Transaction> findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(long userId, Pageable pageable);

  /** How many active transactions reference any of the given categories. */
  long countByUserIdAndDeletedAtIsNullAndCategoryIdIn(long userId, Collection<Long> categoryIds);

  /** The user's active uncategorized transactions of the given types (for re-running rules). */
  List<Transaction> findByUserIdAndDeletedAtIsNullAndCategoryIdIsNullAndTypeIn(
      long userId, Collection<TransactionType> types);

  /** Purge trashed rows past the retention cutoff. */
  @Modifying
  @org.springframework.data.jpa.repository.Query("DELETE FROM Transaction t WHERE t.deletedAt < :cutoff")
  int deleteByDeletedAtBefore(@Param("cutoff") Instant cutoff);
```

Add `AND t.deletedAt IS NULL` to the JPQL dedupe query:
```java
  @Query(
      "SELECT t.dedupeHash FROM Transaction t "
          + "WHERE t.userId = :userId AND t.accountId = :accountId AND t.deletedAt IS NULL")
  List<String> findDedupeHashesByUserIdAndAccountId(
      @Param("userId") long userId, @Param("accountId") long accountId);
```

Add `AND t.deleted_at IS NULL` to **each** of the 5 native queries — inside the `WHERE`, after the existing conditions:
- `summarize`: after `AND t.type IN ('income', 'expense')` add `AND t.deleted_at IS NULL`.
- `accountActivityMinor`: after `AND (t.account_id = :accountId OR t.counter_account_id = :accountId)` add `AND t.deleted_at IS NULL`.
- `sumByCategory`: after `AND t.type = :type` add `AND t.deleted_at IS NULL`.
- `sumByPeriod`: after `AND t.type IN ('income', 'expense')` add `AND t.deleted_at IS NULL`.
- `sumByPeriodAndCategory`: after `AND t.type = :type` add `AND t.deleted_at IS NULL`.

- [ ] **Step 4: Criteria list filter**

In `TransactionService.java`, in the static `filter(...)` method, right after `predicates.add(cb.equal(root.get("userId"), userId));`, add:
```java
      predicates.add(cb.isNull(root.get("deletedAt")));
```

- [ ] **Step 5: Update the renamed-finder call sites**

- `TransactionService.java` `requireOwned` (line ~278): `.findByIdAndUserId(id, userId)` → `.findByIdAndUserIdAndDeletedAtIsNull(id, userId)`.
- `ExportService.java:109`: `transactionRepository.findByUserIdOrderByDateAscIdAsc(userId)` → `transactionRepository.findByUserIdAndDeletedAtIsNullOrderByDateAscIdAsc(userId)`.
- `CategoryService.java:105`: `transactionRepository.countByUserIdAndCategoryIdIn(userId, affectedIds)` → `transactionRepository.countByUserIdAndDeletedAtIsNullAndCategoryIdIn(userId, affectedIds)`.
- `RuleService.java:105`: `transactionRepository.findByUserIdAndCategoryIdIsNullAndTypeIn(userId, CATEGORIZABLE)` → `transactionRepository.findByUserIdAndDeletedAtIsNullAndCategoryIdIsNullAndTypeIn(userId, CATEGORIZABLE)`.

- [ ] **Step 6: Build — existing tests stay green**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build
```
Expected: BUILD SUCCESSFUL. Every existing suite (reporting/breakdown/budgets/export/import/account-balance) still passes — because no rows are soft-deleted yet, the filters are no-ops. If Spotless flags formatting, `./gradlew spotlessApply` and rebuild.

- [ ] **Step 7: Commit** (after the user's go-ahead)

```bash
git add backend/src/main/resources/db/migration/V15__transactions_soft_delete.sql \
        backend/src/main/java/com/financetracker/transaction/Transaction.java \
        backend/src/main/java/com/financetracker/transaction/TransactionRepository.java \
        backend/src/main/java/com/financetracker/transaction/TransactionService.java \
        backend/src/main/java/com/financetracker/export/ExportService.java \
        backend/src/main/java/com/financetracker/category/CategoryService.java \
        backend/src/main/java/com/financetracker/rule/RuleService.java
git commit -m "feat(backend): exclude soft-deleted transactions from all reads (V15)"
```

---

## Task 2: Service + controller + cleanup

**Files:**
- Modify: `backend/src/main/java/com/financetracker/transaction/TransactionService.java`
- Modify: `backend/src/main/java/com/financetracker/transaction/TransactionController.java`
- Create: `backend/src/main/java/com/financetracker/transaction/TrashProperties.java`
- Create: `backend/src/main/java/com/financetracker/transaction/TransactionTrashCleanup.java`

- [ ] **Step 1: Service — soft-delete, restore, permanent, trash list**

In `TransactionService.java`, ensure `import java.time.Instant;` is present. Replace the current `delete(...)`:
```java
  @Transactional
  public void delete(long userId, long id) {
    Transaction tx = requireOwned(userId, id); // active only -> deleting a trashed id is a 404
    tx.setDeletedAt(Instant.now());
    transactionRepository.saveAndFlush(tx);
  }

  @Transactional
  public TransactionResponse restore(long userId, long id) {
    Transaction tx =
        transactionRepository
            .findByIdAndUserIdAndDeletedAtIsNotNull(id, userId)
            .orElseThrow(() -> NotFoundException.of("Transaction", id));
    tx.setDeletedAt(null);
    return toResponse(transactionRepository.saveAndFlush(tx));
  }

  @Transactional
  public void permanentlyDelete(long userId, long id) {
    Transaction tx =
        transactionRepository
            .findByIdAndUserIdAndDeletedAtIsNotNull(id, userId)
            .orElseThrow(() -> NotFoundException.of("Transaction", id));
    transactionRepository.delete(tx);
  }

  @Transactional(readOnly = true)
  public PageResponse<TransactionResponse> listTrash(long userId, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int safePage = Math.max(page, 0);
    Page<Transaction> result =
        transactionRepository.findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(
            userId, PageRequest.of(safePage, safeSize));
    List<TransactionResponse> items = result.getContent().stream().map(this::toResponse).toList();
    return PageResponse.of(items, result);
  }
```

- [ ] **Step 2: Controller — restore / trash / permanent endpoints**

In `TransactionController.java`, add these methods (imports `GetMapping`, `DeleteMapping`, `PathVariable`, `RequestParam`, `PostMapping`, `PageResponse` already present):
```java
  @PostMapping("/{id}/restore")
  public TransactionResponse restore(@CurrentUser AuthUser user, @PathVariable long id) {
    return transactionService.restore(user.id(), id);
  }

  @GetMapping("/trash")
  public PageResponse<TransactionResponse> trash(
      @CurrentUser AuthUser user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return transactionService.listTrash(user.id(), page, size);
  }

  @DeleteMapping("/{id}/permanent")
  public ResponseEntity<Void> permanent(@CurrentUser AuthUser user, @PathVariable long id) {
    transactionService.permanentlyDelete(user.id(), id);
    return ResponseEntity.noContent().build();
  }
```
(The literal `/trash` mapping is matched ahead of `/{id}`, and `"trash"` cannot bind to the `long id` anyway.)

- [ ] **Step 3: Retention properties + cleanup**

Create `backend/src/main/java/com/financetracker/transaction/TrashProperties.java`:
```java
package com.financetracker.transaction;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Trash retention window + purge cron. Picked up by {@code @ConfigurationPropertiesScan}. */
@ConfigurationProperties(prefix = "app.trash")
public record TrashProperties(
    @DefaultValue("30d") Duration retention, @DefaultValue("0 45 3 * * *") String cleanupCron) {}
```

Create `backend/src/main/java/com/financetracker/transaction/TransactionTrashCleanup.java`:
```java
package com.financetracker.transaction;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly purge of trashed transactions past the retention window (mirrors {@code
 * RefreshTokenCleanup}). Idempotent, so running on every scaled-out instance is harmless.
 */
@Component
public class TransactionTrashCleanup {

  private static final Logger log = LoggerFactory.getLogger(TransactionTrashCleanup.class);

  private final TransactionRepository transactionRepository;
  private final TrashProperties properties;

  public TransactionTrashCleanup(
      TransactionRepository transactionRepository, TrashProperties properties) {
    this.transactionRepository = transactionRepository;
    this.properties = properties;
  }

  @Scheduled(cron = "${app.trash.cleanup-cron:0 45 3 * * *}")
  @Transactional
  public void purgeExpired() {
    int deleted =
        transactionRepository.deleteByDeletedAtBefore(Instant.now().minus(properties.retention()));
    if (deleted > 0) {
      log.info("Purged {} trashed transaction(s)", deleted);
    }
  }
}
```

- [ ] **Step 4: Compile check**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL. (Full verification is Task 3.)

---

## Task 3: The core integration test + full build

**Files:**
- Test: `backend/src/test/java/com/financetracker/transaction/TransactionSoftDeleteTest.java`

- [ ] **Step 1: Write the integration test**

Create `backend/src/test/java/com/financetracker/transaction/TransactionSoftDeleteTest.java`:
```java
package com.financetracker.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Soft-delete must remove a transaction from EVERY read path, restore must bring it back, and trash /
 * permanent-delete / retention purge must behave. Fixed month so numbers are deterministic.
 */
class TransactionSoftDeleteTest extends AbstractIntegrationTest {

  @Autowired private TransactionRepository transactionRepository;
  @Autowired private TransactionTrashCleanup transactionTrashCleanup;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void softDeletedTransactionVanishesFromEveryReadThenRestores() throws Exception {
    RegisteredUser user = register("soft-delete@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    createBudget(user, food, 100000); // 1000.00 limit

    long keep = createExpense(user, account, "2026-03-05", 20000, food);
    long trash = createExpense(user, account, "2026-03-20", 30000, food);

    // Baseline: both counted (spent 500.00, balance -500.00).
    assertExpenseTotal(user, 50000);
    assertBudgetSpent(user, 50000);
    assertBalance(user, account, -50000);
    assertThat(listCount(user)).isEqualTo(2);

    // Soft-delete the 300.00 expense.
    mockMvc
        .perform(delete("/api/v1/transactions/" + trash).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());

    // Gone from list, summary, breakdown, cashflow, budget, balance, backup.
    assertThat(listCount(user)).isEqualTo(1);
    assertExpenseTotal(user, 20000);
    assertBudgetSpent(user, 20000);
    assertBalance(user, account, -20000);
    assertThat(backupTransactionCount(user)).isEqualTo(1);
    // Breakdown expense total excludes it.
    mockMvc
        .perform(
            get("/api/v1/reports/breakdown?from=2026-03-01&to=2026-03-31&kind=expense")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinor").value(20000));
    // Trash lists exactly the deleted one; a repeated delete is a 404.
    mockMvc
        .perform(get("/api/v1/transactions/trash").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(trash));
    mockMvc
        .perform(delete("/api/v1/transactions/" + trash).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNotFound());

    // Dedupe excludes deleted: importing a row identical to the deleted one re-creates it.
    mockMvc
        .perform(importOneRow(user, account))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(1));
    // (cleanup: that import added a row back; ignore it for the restore assertions by using the keep id)

    // Restore brings it back into the reports.
    mockMvc
        .perform(
            post("/api/v1/transactions/" + trash + "/restore")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(trash));
    assertBudgetSpent(user, 20000 + 30000 + 30000); // keep + restored + the imported duplicate
  }

  @Test
  void permanentDeleteAndPurgeRemoveTrashedRows() throws Exception {
    RegisteredUser user = register("soft-delete-purge@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    long a = createExpense(user, account, "2026-04-05", 10000, food);
    long b = createExpense(user, account, "2026-04-06", 20000, food);
    softDelete(user, a);
    softDelete(user, b);

    // Permanent-delete one trashed row; restore no longer finds it (404).
    mockMvc
        .perform(
            delete("/api/v1/transactions/" + a + "/permanent")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/v1/transactions/" + a + "/restore")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNotFound());

    // Retention purge removes the other trashed row (cutoff in the future removes all trashed).
    int purged =
        new TransactionTemplate(transactionManager)
            .execute(s -> transactionRepository.deleteByDeletedAtBefore(Instant.now().plusSeconds(5)));
    assertThat(purged).isGreaterThanOrEqualTo(1);
    mockMvc
        .perform(get("/api/v1/transactions/trash").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));

    // The @Scheduled wrapper runs without error (nothing left to purge).
    transactionTrashCleanup.purgeExpired();
  }

  @Test
  void trashIsPerUser() throws Exception {
    RegisteredUser alice = register("soft-a@example.com", "password123");
    RegisteredUser bob = register("soft-b@example.com", "password123");
    long acct = createAccount(alice);
    long food = createCategory(alice, "Food", "expense");
    softDelete(alice, createExpense(alice, acct, "2026-05-05", 10000, food));
    mockMvc
        .perform(get("/api/v1/transactions/trash").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
  }

  // --- helpers ---

  private void softDelete(RegisteredUser user, long id) throws Exception {
    mockMvc
        .perform(delete("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());
  }

  private void assertExpenseTotal(RegisteredUser user, long expected) throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reports/summary?from=2026-03-01&to=2026-03-31")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expenseMinor").value(expected));
  }

  private void assertBudgetSpent(RegisteredUser user, long expected) throws Exception {
    mockMvc
        .perform(get("/api/v1/budgets?month=2026-03").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].spentMinor").value(expected));
  }

  private void assertBalance(RegisteredUser user, long account, long expected) throws Exception {
    mockMvc
        .perform(
            get("/api/v1/accounts/" + account + "/balance")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balanceMinor").value(expected));
  }

  private int listCount(RegisteredUser user) throws Exception {
    MvcResult r =
        mockMvc
            .perform(
                get("/api/v1/transactions?size=200").header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(r.getResponse().getContentAsString()).get("items").size();
  }

  private int backupTransactionCount(RegisteredUser user) throws Exception {
    MvcResult r =
        mockMvc
            .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(r.getResponse().getContentAsString()).get("transactions").size();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder importOneRow(
      RegisteredUser user, long account) {
    // A single CSV row matching the deleted 300.00 expense on 2026-03-20 (same dedupe inputs).
    String csv = "Date;Title;Amount\n20.03.2026;Food;-300,00\n";
    String mapping =
        "{\"delimiter\":\";\",\"encoding\":\"utf-8\",\"hasHeader\":true,\"dateIndex\":0,"
            + "\"dateFormat\":\"auto\",\"descriptionIndex\":1,\"amountMode\":\"signed\","
            + "\"amountIndex\":2,\"expenseIsNegative\":true,\"debitIndex\":-1,\"creditIndex\":-1}";
    return multipart("/api/v1/imports/commit")
        .file(new MockMultipartFile("file", "x.csv", "text/csv", csv.getBytes(UTF_8)))
        .file(new MockMultipartFile("mapping", "", "application/json", mapping.getBytes(UTF_8)))
        .param("accountId", String.valueOf(account))
        .header(HttpHeaders.AUTHORIZATION, bearer(user));
  }

  private long createAccount(RegisteredUser user) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Checking\",\"type\":\"checking\",\"currency\":\"PLN\",\"trackBalance\":true}"))
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

  private void createBudget(RegisteredUser user, long categoryId, long amountMinor) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + categoryId + ",\"amountMinor\":" + amountMinor + "}"))
        .andExpect(status().isCreated());
  }

  private long createExpense(
      RegisteredUser user, long account, String date, long amount, long categoryId) throws Exception {
    return id(
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
                            + ",\"description\":\"Food\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
```

> **Executor note:** confirm the exact JSON field names against the running API before relying on them — `summary` uses `expenseMinor`, breakdown uses `totalMinor`, account balance uses `balanceMinor`, backup uses a `transactions` array. If a name differs, adjust the assertion (the *value* logic is what matters). Grep the DTOs (`SummaryResponse`, `BreakdownResponse`, `BalanceResponse`, backup DTO) if unsure.

- [ ] **Step 2: Run the integration test**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.transaction.TransactionSoftDeleteTest'
```
Expected: PASS (3 tests). Docker must be up.

- [ ] **Step 3: Full backend gate**

Run:
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build
```
Expected: BUILD SUCCESSFUL (Spotless + all tests + JaCoCo ≥ 0.85). `spotlessApply` + rebuild if formatting flagged.

- [ ] **Step 4: Commit** (after the user's go-ahead)

```bash
git add backend/src/main/java/com/financetracker/transaction/ \
        backend/src/test/java/com/financetracker/transaction/TransactionSoftDeleteTest.java
git commit -m "feat(backend): soft-delete + undo/trash for transactions"
```

---

## Task 4: Frontend — undo toast + Trash view

**Files:**
- Modify: `frontend/src/components/Toast.tsx`
- Modify: `frontend/src/features/transactions/api.ts`
- Modify: `frontend/src/features/transactions/hooks.ts`
- Modify: `frontend/src/features/transactions/TransactionsPage.tsx`
- Create: `frontend/src/features/transactions/TrashPage.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/lib/i18n.ts`

- [ ] **Step 1: Toast — an optional action (for "Undo")**

In `Toast.tsx`, extend the `Toast` type, `ToastApi`, `push`, `api`, and the render. Replace the `Toast` interface and `ToastApi`:
```ts
type ToastTone = 'success' | 'error' | 'info'
interface ToastAction {
  label: string
  onClick: () => void
}
interface Toast {
  id: number
  tone: ToastTone
  message: string
  action?: ToastAction
}

interface ToastApi {
  success: (message: string) => void
  error: (message: string) => void
  info: (message: string) => void
  action: (message: string, actionLabel: string, onAction: () => void) => void
}
```
Update `push` to accept an action and `api` to expose it:
```ts
  const push = useCallback(
    (tone: ToastTone, message: string, action?: ToastAction) => {
      const id = nextId.current++
      setToasts((t) => [...t, { id, tone, message, action }])
      setTimeout(() => remove(id), 4500)
    },
    [remove],
  )

  const api = useMemo<ToastApi>(
    () => ({
      success: (m) => push('success', m),
      error: (m) => push('error', m),
      info: (m) => push('info', m),
      action: (m, label, onAction) => push('info', m, { label, onClick: onAction }),
    }),
    [push],
  )
```
Render the action button inside the toast div (replace the `{t.message}` body):
```tsx
            <span className="flex items-center justify-between gap-3">
              {t.message}
              {t.action && (
                <button
                  type="button"
                  className="shrink-0 font-semibold underline underline-offset-2"
                  onClick={(e) => {
                    e.stopPropagation()
                    t.action?.onClick()
                    remove(t.id)
                  }}
                >
                  {t.action.label}
                </button>
              )}
            </span>
```

- [ ] **Step 2: api.ts — restore / trash / permanent**

In `frontend/src/features/transactions/api.ts`, add to `transactionsApi`:
```ts
  restore: (id: number) => api.post<Transaction>(`/api/v1/transactions/${id}/restore`),
  trash: (page = 0, size = 50) =>
    api.get<Page<Transaction>>('/api/v1/transactions/trash', { params: { page, size } }),
  permanent: (id: number) => api.delete<void>(`/api/v1/transactions/${id}/permanent`),
```

- [ ] **Step 3: hooks.ts — restore / trash / permanent**

In `frontend/src/features/transactions/hooks.ts`, add:
```ts
export const trashKeys = { all: ['transactions', 'trash'] as const }

export function useTrash(page = 0, size = 50) {
  return useQuery({
    queryKey: [...trashKeys.all, page, size],
    queryFn: () => transactionsApi.trash(page, size),
  })
}

export function useRestoreTransaction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => transactionsApi.restore(id),
    onSuccess: () => invalidateDerived(qc),
  })
}

export function usePermanentlyDeleteTransaction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => transactionsApi.permanent(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: trashKeys.all }),
  })
}
```
And make `invalidateDerived` also refresh the trash list (a delete/restore moves rows in/out of trash) — add to its body:
```ts
  void qc.invalidateQueries({ queryKey: ['transactions', 'trash'] })
```

- [ ] **Step 4: TransactionsPage — soft-delete with Undo, + a Trash link**

In `TransactionsPage.tsx`, add imports:
```ts
import { Link } from 'react-router'
import { useDeleteTransaction, useRestoreTransaction, useTransactions } from './hooks'
```
Replace the delete handler (lines ~77-84) — no confirm (it's undoable); show an Undo toast:
```ts
  const remove = useDeleteTransaction()
  const restore = useRestoreTransaction()
  const onDelete = (tx: Transaction) => {
    remove.mutate(tx.id, {
      onSuccess: () =>
        toast.action(t('transactions.deleted'), t('transactions.undo'), () =>
          restore.mutate(tx.id, { onError: () => toast.error(t('errors.generic')) }),
        ),
      onError: () => toast.error(t('errors.generic')),
    })
  }
```
Add a **Trash** link in the `PageHeader` actions (next to the New-transaction button — wrap both in a fragment):
```tsx
        actions={
          <>
            <Link
              to="/transactions/trash"
              className="rounded-lg px-3 py-2 text-sm font-medium text-fg-muted hover:text-fg"
            >
              {t('transactions.trash')}
            </Link>
            <Button
              onClick={() => {
                setEditing(undefined)
                setFormOpen(true)
              }}
            >
              {t('transactions.new')}
            </Button>
          </>
        }
```
(Adjust to match the exact existing `actions` JSX — keep the existing New button, just add the Link before it.)

- [ ] **Step 5: TrashPage**

Create `frontend/src/features/transactions/TrashPage.tsx`:
```tsx
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { Transaction } from '@/api'
import { Button, Card, CenteredState, PageHeader, Skeleton } from '@/components/primitives'
import { Money } from '@/components/Money'
import { useToast } from '@/components/Toast'
import { usePermanentlyDeleteTransaction, useRestoreTransaction, useTrash } from './hooks'

export function TrashPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const { data, isLoading, isError, refetch } = useTrash()
  const restore = useRestoreTransaction()
  const permanent = usePermanentlyDeleteTransaction()

  const items = data?.items ?? []

  const onRestore = (tx: Transaction) =>
    restore.mutate(tx.id, {
      onSuccess: () => toast.success(t('transactions.restored')),
      onError: () => toast.error(t('errors.generic')),
    })
  const onPermanent = (tx: Transaction) => {
    if (!window.confirm(t('transactions.deleteForeverConfirm'))) return
    permanent.mutate(tx.id, { onError: () => toast.error(t('errors.generic')) })
  }

  return (
    <>
      <PageHeader
        title={t('transactions.trashTitle')}
        actions={
          <Link
            to="/transactions"
            className="rounded-lg px-3 py-2 text-sm font-medium text-fg-muted hover:text-fg"
          >
            {t('transactions.title')}
          </Link>
        }
      />
      {isLoading ? (
        <Card className="p-5">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="my-2 h-10 w-full" />
          ))}
        </Card>
      ) : isError ? (
        <CenteredState
          title={t('errors.loadFailed')}
          action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>}
        />
      ) : !items.length ? (
        <CenteredState title={t('transactions.trashEmpty')} />
      ) : (
        <div className="space-y-2">
          {items.map((tx) => (
            <Card key={tx.id} className="flex items-center justify-between gap-3 p-3">
              <div className="min-w-0 text-sm">
                <span className="text-fg-muted">{tx.date}</span>{' '}
                <span className="font-medium text-fg">{tx.description || '—'}</span>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Money minor={tx.amountMinor} currency={tx.currency} />
                <Button variant="ghost" size="sm" onClick={() => onRestore(tx)}>
                  {t('transactions.restore')}
                </Button>
                <Button variant="ghost" size="sm" onClick={() => onPermanent(tx)}>
                  {t('transactions.deleteForever')}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </>
  )
}
```
(If any imported primitive name differs, grep `components/primitives` — `Card`, `Button`, `PageHeader`, `Skeleton`, `CenteredState`, `Money` are all used by `BudgetsPage`.)

- [ ] **Step 6: Route**

In `frontend/src/app/App.tsx`, import `TrashPage` and add a route inside the `RequireAuth` block, right after the `/transactions` route:
```tsx
        <Route path="/transactions/trash" element={<TrashPage />} />
```
Import: `import { TrashPage } from '@/features/transactions/TrashPage'` (match the existing import style for pages).

- [ ] **Step 7: i18n**

In `frontend/src/lib/i18n.ts`, add to the **English** `transactions` block (after the `deleteConfirm: ...` line):
```ts
        deleted: 'Transaction deleted', undo: 'Undo', restored: 'Transaction restored',
        trash: 'Trash', trashTitle: 'Deleted transactions', trashEmpty: 'Trash is empty.',
        restore: 'Restore', deleteForever: 'Delete forever',
        deleteForeverConfirm: 'Permanently delete this transaction? This cannot be undone.',
```
And the **Polish** `transactions` block (after its `deleteConfirm`/`rateHint` line):
```ts
        deleted: 'Usunięto transakcję', undo: 'Cofnij', restored: 'Przywrócono transakcję',
        trash: 'Kosz', trashTitle: 'Usunięte transakcje', trashEmpty: 'Kosz jest pusty.',
        restore: 'Przywróć', deleteForever: 'Usuń trwale',
        deleteForeverConfirm: 'Trwale usunąć tę transakcję? Tej operacji nie można cofnąć.',
```

- [ ] **Step 8: Frontend gate**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/frontend
npm run lint && npm test && npm run build
```
Expected: eslint clean (pre-existing warnings only), Vitest green, `tsc -b` + vite build succeed.

- [ ] **Step 9: Commit** (after the user's go-ahead)

```bash
git add frontend/src/components/Toast.tsx frontend/src/features/transactions/ \
        frontend/src/app/App.tsx frontend/src/lib/i18n.ts
git commit -m "feat(frontend): soft-delete undo toast + Trash view"
```

---

## Task 5: Boundary — verify + document

- [ ] **Step 1: One-off throwaway check** — with the stack up: delete a transaction → Undo toast restores it; delete again → Trash page → Restore and Delete-forever. Don't commit an E2E spec.
- [ ] **Step 2: Update `HANDOFF.md` + `CLAUDE.md`** (local-only): item **C (soft-delete/undo)** delivered — V15 `deleted_at`, all reads filtered, restore/trash/permanent + 30-day purge, undo toast + Trash view. Migration table → V15; **next migration V16**; next backlog item **D (bulk transaction ops)**. Update the §17 checklist.
- [ ] **Step 3: Stop at the phase boundary.** Report green builds with output; push only when asked.

---

## Self-review notes (author)

- **Spec coverage:** all read paths filtered — 5 native + JPQL dedupe + Criteria list + 4 renamed derived finders + call sites (Task 1); soft-delete/restore/permanent/trash + 30d purge (Task 2); the core "vanishes-from-everything + restore + trash + purge + isolation" integration test (Task 3); undo toast + Trash page + route + i18n (Task 4). Recurring dedupe covered by the shared `findDedupeHashes` filter.
- **Placeholder scan:** the Task-3 executor note (verify JSON field names) is a deliberate safety check, not a missing step — the assertions are complete and named; only confirm the property spellings.
- **Type/name consistency:** `deletedAt` (entity) / `deleted_at` (SQL), `findByIdAndUserIdAndDeletedAtIsNull` / `...IsNotNull`, `findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc`, `deleteByDeletedAtBefore`, `restore`/`permanentlyDelete`/`listTrash`, and `toast.action(message,label,onAction)` are used identically across tasks.
- **Behavior preserved:** Task 1's filters are no-ops on existing (non-deleted) data, so the existing to-the-grosz suites are the safety net; Task 3 proves the deleted case.
