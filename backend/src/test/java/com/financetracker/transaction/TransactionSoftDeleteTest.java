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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Soft-delete must remove a transaction from EVERY read path, restore must bring it back, and trash
 * / permanent-delete / retention purge must behave. Fixed month so numbers are deterministic.
 */
class TransactionSoftDeleteTest extends AbstractIntegrationTest {

  @Autowired private TransactionRepository transactionRepository;
  @Autowired private TransactionTrashCleanup transactionTrashCleanup;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void softDeletedVanishesFromEveryReadThenRestores() throws Exception {
    RegisteredUser user = register("soft-delete@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    createBudget(user, food, 100000);

    createExpense(user, account, "2026-03-05", 20000, food); // keep
    long trash = createExpense(user, account, "2026-03-20", 30000, food); // will delete

    // Baseline: both counted everywhere.
    assertExpenseTotal(user, 50000);
    assertBreakdownTotal(user, 50000);
    assertBudgetSpent(user, 50000);
    assertBalance(user, account, -50000);
    assertThat(listCount(user)).isEqualTo(2);
    assertThat(backupCount(user)).isEqualTo(2);

    softDelete(user, trash);

    // Gone from list, summary, breakdown, budget, balance, backup.
    assertExpenseTotal(user, 20000);
    assertBreakdownTotal(user, 20000);
    assertBudgetSpent(user, 20000);
    assertBalance(user, account, -20000);
    assertThat(listCount(user)).isEqualTo(1);
    assertThat(backupCount(user)).isEqualTo(1);

    // Trash lists exactly it; re-deleting the trashed id is a 404.
    mockMvc
        .perform(get("/api/v1/transactions/trash").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(trash));
    mockMvc
        .perform(
            delete("/api/v1/transactions/" + trash).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNotFound());

    // Restore brings it back everywhere.
    mockMvc
        .perform(
            post("/api/v1/transactions/" + trash + "/restore")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(trash));
    assertExpenseTotal(user, 50000);
    assertBudgetSpent(user, 50000);
    assertBalance(user, account, -50000);
    assertThat(listCount(user)).isEqualTo(2);
    mockMvc
        .perform(get("/api/v1/transactions/trash").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void softDeletedRowDoesNotBlockReimport() throws Exception {
    RegisteredUser user = register("soft-delete-dedupe@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    long id = createExpense(user, account, "2026-03-20", 30000, food);
    softDelete(user, id);
    // A deleted row does not occupy its dedupe hash: an identical CSV row imports rather than
    // dedupes.
    mockMvc
        .perform(importOneRow(user, account))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(1))
        .andExpect(jsonPath("$.skippedDuplicates").value(0));
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

    // Retention purge (future cutoff removes all trashed); the modifying query needs a transaction.
    int purged =
        new TransactionTemplate(transactionManager)
            .execute(
                s -> transactionRepository.deleteByDeletedAtBefore(Instant.now().plusSeconds(5)));
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
        .perform(
            delete("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
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

  private void assertBreakdownTotal(RegisteredUser user, long expected) throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reports/breakdown?from=2026-03-01&to=2026-03-31&kind=expense")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalBaseMinor").value(expected));
  }

  private void assertBudgetSpent(RegisteredUser user, long expected) throws Exception {
    mockMvc
        .perform(
            get("/api/v1/budgets?month=2026-03").header(HttpHeaders.AUTHORIZATION, bearer(user)))
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
                get("/api/v1/transactions?size=200")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(r.getResponse().getContentAsString()).get("items").size();
  }

  private int backupCount(RegisteredUser user) throws Exception {
    MvcResult r =
        mockMvc
            .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(r.getResponse().getContentAsString()).get("transactions").size();
  }

  private MockHttpServletRequestBuilder importOneRow(RegisteredUser user, long account) {
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

  private void createBudget(RegisteredUser user, long categoryId, long amountMinor)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + categoryId + ",\"amountMinor\":" + amountMinor + "}"))
        .andExpect(status().isCreated());
  }

  private long createExpense(
      RegisteredUser user, long account, String date, long amount, long categoryId)
      throws Exception {
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
