# Bulk Transaction Ops Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Multi-select transactions in the list and bulk **delete** (soft, undoable) or **recategorize** them in one atomic action (+ bulk-restore to back the undo).

**Architecture:** Three focused endpoints (`bulk-delete`, `bulk-restore`, `bulk-categorize`) over the existing tables — each loads the whole selection with a scoped finder, validates it atomically (404 if any id isn't the user's/active; 422 on kind-mismatch/transfer for categorize), then `saveAll`. Frontend adds a checkbox column + an action bar.

**Tech Stack:** Spring Boot 3.5 / Java 21 / JPA (backend); React 19 + TS (frontend). Build with **Java 21** (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`), absolute paths. **No migration** (next free stays V16).

**Spec:** `docs/superpowers/specs/2026-07-31-bulk-transaction-ops-design.md`

---

## Status (refreshed 2026-09-01)

All implementation code for Tasks 0–3 is **written on disk (uncommitted)** on branch
`backlog-bulk-transaction-ops`, and **both gates now pass**:

- **Backend gate** ✅ — `spotlessApply build` BUILD SUCCESSFUL (Spotless + all tests, incl. `BulkTransactionOpsTest`, + JaCoCo ≥ 0.85).
- **Frontend gate** ✅ — lint 0 errors (5 pre-existing `watch()` warnings), Vitest 26/26, `tsc -b` + vite build succeed.

Three code blocks below were corrected to match what actually shipped:

- **`BulkActionBar.tsx`** — inlines the `categories` prop type instead of importing `Category` from `@/api`.
- **`BulkTransactionOpsTest`** — imports `MockHttpServletRequestBuilder` rather than fully-qualifying it inline.
- **`TransactionsPage.tsx`** — `selectionKind` binds `firstSelected = selectedTxs[0]` and truthy-narrows it; the
  original `selectedTxs[0].type` failed `tsc` (TS2532) under `noUncheckedIndexedAccess`. **This fix was applied to
  the source on 2026-09-01 to get the build green.**

**Not yet done:** both commits (Task 2 Step 3, Task 3 Step 7) and the whole boundary (Task 4 — one-off in-app check,
HANDOFF/CLAUDE updates). Next action per §17: pause for the user before each commit (backend then frontend, separate).

---

## Standing rules for the executor (project §17)

- **Commit only when the user asks; push only when the user asks.** Pause before each `Commit` step (backend then frontend, separate).
- **Backend first, then frontend.** Keep both green.
- **Stop at the phase boundary** (after Task 4) for in-app testing.
- Local-only docs are git-ignored — update on disk, never commit.

---

## Task 0: Branch

- [ ] **Step 1: Branch off `main`**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
git checkout main && git switch -c backlog-bulk-transaction-ops
```
Expected: `Switched to a new branch 'backlog-bulk-transaction-ops'`.

---

## Task 1: Backend — DTOs, repository, service, controller

**Files:**
- Create: `backend/src/main/java/com/financetracker/transaction/dto/BulkIdsRequest.java`
- Create: `backend/src/main/java/com/financetracker/transaction/dto/BulkCategorizeRequest.java`
- Create: `backend/src/main/java/com/financetracker/transaction/dto/BulkResult.java`
- Modify: `backend/src/main/java/com/financetracker/transaction/TransactionRepository.java`
- Modify: `backend/src/main/java/com/financetracker/transaction/TransactionService.java`
- Modify: `backend/src/main/java/com/financetracker/transaction/TransactionController.java`

- [ ] **Step 1: DTOs**

`BulkIdsRequest.java`:
```java
package com.financetracker.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A bulk selection of transaction ids (delete / restore). Bounded so a request can't be unbounded. */
public record BulkIdsRequest(@NotEmpty @Size(max = 500) List<Long> ids) {}
```

`BulkCategorizeRequest.java`:
```java
package com.financetracker.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Bulk recategorize: set {@code categoryId} (null = uncategorize) on the selected transactions. */
public record BulkCategorizeRequest(
    @NotEmpty @Size(max = 500) List<Long> ids, Long categoryId) {}
```

`BulkResult.java`:
```java
package com.financetracker.transaction.dto;

/** How many transactions a bulk action affected. */
public record BulkResult(int affected) {}
```

- [ ] **Step 2: Repository finders**

In `TransactionRepository.java`, add (below the other soft-delete finders; `Collection` is already imported):
```java
  /** The active, owned subset of the given ids (delete / categorize targets). */
  List<Transaction> findByIdInAndUserIdAndDeletedAtIsNull(Collection<Long> ids, long userId);

  /** The trashed, owned subset of the given ids (restore targets). */
  List<Transaction> findByIdInAndUserIdAndDeletedAtIsNotNull(Collection<Long> ids, long userId);
```

- [ ] **Step 3: Service — bulk operations**

In `TransactionService.java`, add imports:
```java
import java.util.LinkedHashSet;
import java.util.Set;
```
Add these methods (near `delete`/`restore`). They de-duplicate the ids and compare counts so a missing/foreign id fails the whole batch (atomic, rolls back):
```java
  @Transactional
  public int bulkDelete(long userId, List<Long> ids) {
    List<Transaction> txs = requireAllActive(userId, ids);
    Instant now = Instant.now();
    txs.forEach(t -> t.setDeletedAt(now));
    transactionRepository.saveAll(txs);
    return txs.size();
  }

  @Transactional
  public int bulkRestore(long userId, List<Long> ids) {
    Set<Long> distinct = new LinkedHashSet<>(ids);
    List<Transaction> txs =
        transactionRepository.findByIdInAndUserIdAndDeletedAtIsNotNull(distinct, userId);
    if (txs.size() != distinct.size()) {
      throw new NotFoundException("One or more transactions were not found in the trash.");
    }
    txs.forEach(t -> t.setDeletedAt(null));
    transactionRepository.saveAll(txs);
    return txs.size();
  }

  @Transactional
  public int bulkCategorize(long userId, List<Long> ids, Long categoryId) {
    List<Transaction> txs = requireAllActive(userId, ids);
    // resolveCategoryId validates ownership + kind + rejects transfers, per transaction (atomic).
    txs.forEach(t -> t.setCategoryId(resolveCategoryId(userId, t.getType(), categoryId)));
    transactionRepository.saveAll(txs);
    return txs.size();
  }

  private List<Transaction> requireAllActive(long userId, List<Long> ids) {
    Set<Long> distinct = new LinkedHashSet<>(ids);
    List<Transaction> txs =
        transactionRepository.findByIdInAndUserIdAndDeletedAtIsNull(distinct, userId);
    if (txs.size() != distinct.size()) {
      throw new NotFoundException("One or more transactions were not found.");
    }
    return txs;
  }
```
(`resolveCategoryId` already exists and throws `UnprocessableEntityException` for a transfer or kind mismatch, `NotFoundException` for an unowned category — so a bad category fails the batch. `Instant` is imported from item C.)

- [ ] **Step 4: Controller — three endpoints**

In `TransactionController.java`, add imports:
```java
import com.financetracker.transaction.dto.BulkCategorizeRequest;
import com.financetracker.transaction.dto.BulkIdsRequest;
import com.financetracker.transaction.dto.BulkResult;
```
Add these methods before the closing brace (after `permanent`):
```java
  @PostMapping("/bulk-delete")
  public BulkResult bulkDelete(@CurrentUser AuthUser user, @Valid @RequestBody BulkIdsRequest request) {
    return new BulkResult(transactionService.bulkDelete(user.id(), request.ids()));
  }

  @PostMapping("/bulk-restore")
  public BulkResult bulkRestore(
      @CurrentUser AuthUser user, @Valid @RequestBody BulkIdsRequest request) {
    return new BulkResult(transactionService.bulkRestore(user.id(), request.ids()));
  }

  @PostMapping("/bulk-categorize")
  public BulkResult bulkCategorize(
      @CurrentUser AuthUser user, @Valid @RequestBody BulkCategorizeRequest request) {
    return new BulkResult(
        transactionService.bulkCategorize(user.id(), request.ids(), request.categoryId()));
  }
```

- [ ] **Step 5: Compile check**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

---

## Task 2: Backend integration test + build

**Files:**
- Test: `backend/src/test/java/com/financetracker/transaction/BulkTransactionOpsTest.java`

- [ ] **Step 1: Write the test**

Create `backend/src/test/java/com/financetracker/transaction/BulkTransactionOpsTest.java`:
```java
package com.financetracker.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Bulk delete / restore / categorize: atomic validation, soft-delete reuse, per-user isolation. */
class BulkTransactionOpsTest extends AbstractIntegrationTest {

  @Test
  void bulkDeleteRemovesAllSelectedAtomically() throws Exception {
    RegisteredUser user = register("bulk-del@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    long a = createExpense(user, account, 1000, food);
    long b = createExpense(user, account, 2000, food);
    long c = createExpense(user, account, 3000, food);

    // Delete a + b: both gone from the list, both in trash.
    mockMvc
        .perform(bulk(user, "/bulk-delete", "{\"ids\":[" + a + "," + b + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.affected").value(2));
    assertThat(listCount(user)).isEqualTo(1);
    mockMvc
        .perform(get("/api/v1/transactions/trash").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2));

    // Atomic: a batch with an unowned id changes nothing (404), c stays active.
    mockMvc
        .perform(bulk(user, "/bulk-delete", "{\"ids\":[" + c + ",999999]}"))
        .andExpect(status().isNotFound());
    assertThat(listCount(user)).isEqualTo(1);

    // Restore a + b.
    mockMvc
        .perform(bulk(user, "/bulk-restore", "{\"ids\":[" + a + "," + b + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.affected").value(2));
    assertThat(listCount(user)).isEqualTo(3);
  }

  @Test
  void bulkCategorizeSetsAndUncategorizesAtomically() throws Exception {
    RegisteredUser user = register("bulk-cat@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    long a = createExpense(user, account, 1000, food);
    long b = createExpense(user, account, 2000, food);

    // Uncategorize both (categoryId null).
    mockMvc
        .perform(bulk(user, "/bulk-categorize", "{\"ids\":[" + a + "," + b + "],\"categoryId\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.affected").value(2));
    // Recategorize both to Food.
    mockMvc
        .perform(
            bulk(user, "/bulk-categorize", "{\"ids\":[" + a + "," + b + "],\"categoryId\":" + food + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.affected").value(2));

    // Atomic 422: an income tx cannot take an expense category — nothing changes.
    long salary = createCategory(user, "Salary", "income");
    long income = createIncome(user, account, 5000, salary);
    mockMvc
        .perform(
            bulk(
                user,
                "/bulk-categorize",
                "{\"ids\":[" + a + "," + income + "],\"categoryId\":" + food + "}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void bulkOpsAreScopedPerUser() throws Exception {
    RegisteredUser alice = register("bulk-a@example.com", "password123");
    RegisteredUser bob = register("bulk-b@example.com", "password123");
    long acct = createAccount(alice);
    long food = createCategory(alice, "Food", "expense");
    long aliceTx = createExpense(alice, acct, 1000, food);
    // Bob cannot bulk-delete Alice's transaction.
    mockMvc
        .perform(bulk(bob, "/bulk-delete", "{\"ids\":[" + aliceTx + "]}"))
        .andExpect(status().isNotFound());
    assertThat(listCount(alice)).isEqualTo(1);
  }

  // --- helpers ---

  private MockHttpServletRequestBuilder bulk(RegisteredUser user, String path, String body) {
    return post("/api/v1/transactions" + path)
        .header(HttpHeaders.AUTHORIZATION, bearer(user))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
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

  private long createExpense(RegisteredUser user, long account, long amount, long categoryId)
      throws Exception {
    return createTx(user, account, amount, "expense", categoryId);
  }

  private long createIncome(RegisteredUser user, long account, long amount, long categoryId)
      throws Exception {
    return createTx(user, account, amount, "income", categoryId);
  }

  private long createTx(RegisteredUser user, long account, long amount, String type, long categoryId)
      throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/transactions")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"date\":\"2026-03-05\",\"amountMinor\":"
                            + amount
                            + ",\"type\":\""
                            + type
                            + "\",\"accountId\":"
                            + account
                            + ",\"categoryId\":"
                            + categoryId
                            + "}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
```

- [ ] **Step 2: Run the test, then the full gate**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.transaction.BulkTransactionOpsTest'
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessApply build
```
Expected: the targeted test PASSes (3 tests); `build` BUILD SUCCESSFUL (Spotless + all tests + JaCoCo ≥ 0.85).

- [ ] **Step 3: Commit** (after the user's go-ahead)

```bash
git add backend/src/main/java/com/financetracker/transaction/ \
        backend/src/test/java/com/financetracker/transaction/BulkTransactionOpsTest.java
git commit -m "feat(backend): bulk transaction ops (delete/restore/categorize)"
```

---

## Task 3: Frontend — multi-select + action bar

**Files:**
- Modify: `frontend/src/features/transactions/api.ts`
- Modify: `frontend/src/features/transactions/hooks.ts`
- Create: `frontend/src/features/transactions/BulkActionBar.tsx`
- Modify: `frontend/src/features/transactions/TransactionsPage.tsx`
- Modify: `frontend/src/lib/i18n.ts`

- [ ] **Step 1: api.ts — bulk calls**

Add to `transactionsApi` (before the closing `}`):
```ts
  bulkDelete: (ids: number[]) => api.post<{ affected: number }>('/api/v1/transactions/bulk-delete', { ids }),
  bulkRestore: (ids: number[]) => api.post<{ affected: number }>('/api/v1/transactions/bulk-restore', { ids }),
  bulkCategorize: (ids: number[], categoryId: number | null) =>
    api.post<{ affected: number }>('/api/v1/transactions/bulk-categorize', { ids, categoryId }),
```

- [ ] **Step 2: hooks.ts — bulk mutations**

Add (they reuse `invalidateDerived`, already defined):
```ts
export function useBulkDelete() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => transactionsApi.bulkDelete(ids),
    onSuccess: () => invalidateDerived(qc),
  })
}

export function useBulkRestore() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => transactionsApi.bulkRestore(ids),
    onSuccess: () => invalidateDerived(qc),
  })
}

export function useBulkCategorize() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ ids, categoryId }: { ids: number[]; categoryId: number | null }) =>
      transactionsApi.bulkCategorize(ids, categoryId),
    onSuccess: () => invalidateDerived(qc),
  })
}
```

- [ ] **Step 3: BulkActionBar component**

Create `frontend/src/features/transactions/BulkActionBar.tsx`:
```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Select } from '@/components/primitives'

export function BulkActionBar({
  count,
  categories,
  canCategorize,
  onClear,
  onDelete,
  onCategorize,
}: {
  count: number
  categories: { id: number; name: string; parentId: number | null }[]
  canCategorize: boolean
  onClear: () => void
  onDelete: () => void
  onCategorize: (categoryId: number | null) => void
}) {
  const { t } = useTranslation()
  const [choice, setChoice] = useState('')

  // Mirror TransactionForm's "Parent / Child" label for nested categories.
  const label = (c: { id: number; name: string; parentId: number | null }) => {
    if (c.parentId == null) return c.name
    const parent = categories.find((x) => x.id === c.parentId)
    return parent ? `${parent.name} / ${c.name}` : c.name
  }

  const apply = () => {
    if (!choice) return
    onCategorize(choice === 'none' ? null : Number(choice))
    setChoice('')
  }

  return (
    <div className="mb-4 flex flex-wrap items-center gap-3 rounded-lg border border-border bg-surface-2 px-4 py-2.5 text-sm">
      <span className="font-medium text-fg">{t('transactions.selectedCount', { count })}</span>
      <div className="ml-auto flex flex-wrap items-center gap-2">
        <Select
          aria-label={t('transactions.recategorize')}
          value={choice}
          disabled={!canCategorize}
          title={canCategorize ? undefined : t('transactions.recategorizeHint')}
          onChange={(e) => setChoice(e.target.value)}
        >
          <option value="">{t('transactions.recategorize')}…</option>
          <option value="none">{t('transactions.uncategorize')}</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {label(c)}
            </option>
          ))}
        </Select>
        <Button variant="secondary" size="sm" disabled={!canCategorize || !choice} onClick={apply}>
          {t('common.save')}
        </Button>
        <Button variant="ghost" size="sm" onClick={onDelete}>
          {t('common.delete')}
        </Button>
        <Button variant="ghost" size="sm" onClick={onClear}>
          {t('common.cancel')}
        </Button>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: TransactionsPage — selection state + checkbox column + bar**

Add imports:
```ts
import { useBulkCategorize, useBulkDelete, useBulkRestore, useDeleteTransaction, useRestoreTransaction, useTransactions } from './hooks'
import { BulkActionBar } from './BulkActionBar'
```
Inside the component, add selection state + derived values + handlers (place after the existing `onDelete`):
```ts
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const clearSelection = () => setSelected(new Set())
  const toggle = (id: number) =>
    setSelected((s) => {
      const next = new Set(s)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  const pageIds = (data?.items ?? []).map((tx) => tx.id)
  const allSelected = pageIds.length > 0 && pageIds.every((id) => selected.has(id))
  const toggleAll = () => setSelected(allSelected ? new Set() : new Set(pageIds))

  const selectedTxs = (data?.items ?? []).filter((tx) => selected.has(tx.id))
  const kinds = new Set(selectedTxs.map((tx) => tx.type))
  // Bind the first element to a local: under noUncheckedIndexedAccess, selectedTxs[0]
  // is possibly-undefined, and a `.length > 0` guard does not narrow the index access.
  const firstSelected = selectedTxs[0]
  const selectionKind =
    firstSelected && kinds.size === 1 && !kinds.has('transfer')
      ? (firstSelected.type as 'expense' | 'income')
      : null
  const { data: bulkCategories } = useCategories(selectionKind ?? 'expense')

  const bulkDelete = useBulkDelete()
  const bulkRestore = useBulkRestore()
  const bulkCategorize = useBulkCategorize()
  const onBulkDelete = () => {
    const ids = [...selected]
    bulkDelete.mutate(ids, {
      onSuccess: () => {
        clearSelection()
        toast.action(t('transactions.bulkDeleted', { count: ids.length }), t('transactions.undo'), () =>
          bulkRestore.mutate(ids, { onError: () => toast.error(t('errors.generic')) }),
        )
      },
      onError: () => toast.error(t('errors.generic')),
    })
  }
  const onBulkCategorize = (categoryId: number | null) => {
    const ids = [...selected]
    bulkCategorize.mutate(
      { ids, categoryId },
      {
        onSuccess: () => {
          clearSelection()
          toast.success('✓')
        },
        onError: () => toast.error(t('errors.generic')),
      },
    )
  }
```
Render the bar just before the results block (before `{isLoading ? (`):
```tsx
      {selected.size > 0 && (
        <BulkActionBar
          count={selected.size}
          categories={bulkCategories ?? []}
          canCategorize={selectionKind != null}
          onClear={clearSelection}
          onDelete={onBulkDelete}
          onCategorize={onBulkCategorize}
        />
      )}
```
Add the header checkbox `<th>` as the **first** column (before the date `<th>` at line ~177):
```tsx
                  <th className="px-4 py-3">
                    <input
                      type="checkbox"
                      aria-label={t('transactions.selectAll')}
                      className="h-4 w-4 rounded border-border"
                      checked={allSelected}
                      onChange={toggleAll}
                    />
                  </th>
```
Add the per-row checkbox `<td>` as the **first** cell of each row (before the date `<td>` at line ~202):
```tsx
                    <td className="px-4 py-3">
                      <input
                        type="checkbox"
                        aria-label={t('transactions.selectRow')}
                        className="h-4 w-4 rounded border-border"
                        checked={selected.has(tx.id)}
                        onChange={() => toggle(tx.id)}
                      />
                    </td>
```
(The empty trailing actions `<th className="px-4 py-3" />` already spans the extra column count; the checkbox column simply adds one more `<th>`/`<td>` pair — no colspan elsewhere to adjust.)

- [ ] **Step 5: i18n**

Add to the **English** `transactions` block (with the item-C keys):
```ts
        selectedCount: '{{count}} selected', selectAll: 'Select all', selectRow: 'Select row',
        recategorize: 'Recategorize', recategorizeHint: 'Select transactions of a single kind (no transfers) to recategorize.',
        uncategorize: 'Uncategorize', bulkDeleted: '{{count}} deleted',
```
Add to the **Polish** `transactions` block:
```ts
        selectedCount: 'Zaznaczono: {{count}}', selectAll: 'Zaznacz wszystkie', selectRow: 'Zaznacz wiersz',
        recategorize: 'Zmień kategorię', recategorizeHint: 'Wybierz transakcje jednego rodzaju (bez przelewów), aby zmienić kategorię.',
        uncategorize: 'Usuń kategorię', bulkDeleted: 'Usunięto: {{count}}',
```

- [ ] **Step 6: Frontend gate**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/frontend
npm run lint && npm test && npm run build
```
Expected: eslint clean (pre-existing warnings only), Vitest green, `tsc -b` + vite build succeed.

- [ ] **Step 7: Commit** (after the user's go-ahead)

```bash
git add frontend/src/features/transactions/ frontend/src/lib/i18n.ts
git commit -m "feat(frontend): bulk-select transactions (delete/recategorize)"
```

---

## Task 4: Boundary — verify + document

- [ ] **Step 1: One-off throwaway check** — select rows → Delete → Undo restores; select same-kind rows → Recategorize; select mixed/transfer → recategorize disabled. Don't commit an E2E spec.
- [ ] **Step 2: Update `HANDOFF.md` + `CLAUDE.md`** (local-only): item **D (bulk transaction ops)** delivered — bulk delete/restore/categorize endpoints, atomic, multi-select + action bar. **No migration** (next free stays **V16**). Next backlog item **E (saved views/filters)**. Update the §17 checklist.
- [ ] **Step 3: Stop at the phase boundary.** Report green builds; push only when asked.

---

## Self-review notes (author)

- **Spec coverage:** 3 endpoints + DTOs + atomic finders (Task 1); atomic delete/restore/categorize with 404/422 + isolation (Task 2); multi-select + action bar + undo + kind-gated recategorize + i18n (Task 3). No migration — matches spec §7.
- **Placeholder scan:** none — all steps carry real code. The two TransactionsPage checkbox insertions name exact anchors (before the date `<th>`/`<td>`).
- **Type consistency:** `bulkDelete/bulkRestore/bulkCategorize(ids)` and `{ affected }` used identically across api/hooks/service/controller; `BulkIdsRequest`/`BulkCategorizeRequest`/`BulkResult`; `findByIdInAndUserIdAndDeletedAtIsNull`/`…IsNotNull`; `resolveCategoryId` reused for per-tx validation.
- **Reuse:** bulk delete/restore reuse item C's `deleted_at` + Trash; recategorize reuses the existing `resolveCategoryId` invariant; no new migration or scheduled work.
