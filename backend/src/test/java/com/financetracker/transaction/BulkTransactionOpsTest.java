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
        .perform(
            bulk(user, "/bulk-categorize", "{\"ids\":[" + a + "," + b + "],\"categoryId\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.affected").value(2));
    // Recategorize both to Food.
    mockMvc
        .perform(
            bulk(
                user,
                "/bulk-categorize",
                "{\"ids\":[" + a + "," + b + "],\"categoryId\":" + food + "}"))
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
                get("/api/v1/transactions?size=200")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
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

  private long createTx(
      RegisteredUser user, long account, long amount, String type, long categoryId)
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
