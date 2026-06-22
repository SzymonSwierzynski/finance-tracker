package com.financetracker.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Transaction creation (including the FX rate-locking rule and transfer invariants), filtered/paged
 * listing, optimistic-locked edits, deletion, and cross-user isolation.
 */
class TransactionIsolationTest extends AbstractIntegrationTest {

  @Test
  void reportingCurrencyResolvesRateToOne() throws Exception {
    RegisteredUser user = register("tx-base@example.com", "password123");
    long account = createAccount(user, "Checking", "PLN");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transactions")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"date\":\"2024-02-10\",\"amountMinor\":1999,\"type\":\"expense\",\"accountId\":"
                            + account
                            + ",\"description\":\"Coffee\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currency").value("PLN"))
            .andExpect(jsonPath("$.rateToBase").value(1))
            .andExpect(jsonPath("$.baseMinor").value(1999))
            .andExpect(jsonPath("$.dedupeHash").isNotEmpty())
            .andReturn();
    // baseMinor equals amountMinor when the rate is 1.
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    org.assertj.core.api.Assertions.assertThat(body.get("baseMinor").asLong())
        .isEqualTo(body.get("amountMinor").asLong());
  }

  @Test
  void foreignCurrencyNeedsAnExplicitRate() throws Exception {
    RegisteredUser user = register("tx-fx@example.com", "password123");
    long account = createAccount(user, "Euro wallet", "PLN");

    // EUR with no rate (base is PLN) -> 422.
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2024-02-10\",\"amountMinor\":10000,\"type\":\"expense\",\"accountId\":"
                        + account
                        + ",\"currency\":\"EUR\"}"))
        .andExpect(status().isUnprocessableEntity());

    // EUR with an explicit rate -> 201, base locked at round(10000 * 4.30) = 43000.
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2024-02-10\",\"amountMinor\":10000,\"type\":\"expense\",\"accountId\":"
                        + account
                        + ",\"currency\":\"EUR\",\"rateToBase\":4.30}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.baseMinor").value(43000));
  }

  @Test
  void transferRequiresADistinctCounterAccount() throws Exception {
    RegisteredUser user = register("tx-transfer@example.com", "password123");
    long checking = createAccount(user, "Checking", "PLN");
    long savings = createAccount(user, "Savings", "PLN");

    // Missing counter account -> 422.
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2024-02-10\",\"amountMinor\":5000,\"type\":\"transfer\",\"accountId\":"
                        + checking
                        + "}"))
        .andExpect(status().isUnprocessableEntity());

    // Counter == account -> 422.
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2024-02-10\",\"amountMinor\":5000,\"type\":\"transfer\",\"accountId\":"
                        + checking
                        + ",\"counterAccountId\":"
                        + checking
                        + "}"))
        .andExpect(status().isUnprocessableEntity());

    // Valid transfer -> 201, category forced null, counter recorded.
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2024-02-10\",\"amountMinor\":5000,\"type\":\"transfer\",\"accountId\":"
                        + checking
                        + ",\"counterAccountId\":"
                        + savings
                        + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.counterAccountId").value(savings))
        .andExpect(jsonPath("$.categoryId").doesNotExist());
  }

  @Test
  void listSupportsFiltersAndPagination() throws Exception {
    RegisteredUser user = register("tx-list@example.com", "password123");
    long account = createAccount(user, "Checking", "PLN");
    createTx(user, "2024-01-01", 100, "income", account);
    createTx(user, "2024-01-02", 200, "expense", account);
    createTx(user, "2024-01-03", 300, "expense", account);

    // Page 0, size 2 of 3 total; newest date first.
    mockMvc
        .perform(
            get("/api/v1/transactions?page=0&size=2")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].date").value("2024-01-03"));

    // Filter by type.
    mockMvc
        .perform(
            get("/api/v1/transactions?type=expense")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(jsonPath("$.total").value(2));

    // Filter by date range (inclusive).
    mockMvc
        .perform(
            get("/api/v1/transactions?from=2024-01-02&to=2024-01-02")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void editAndDeleteWithOptimisticLocking() throws Exception {
    RegisteredUser user = register("tx-edit@example.com", "password123");
    long account = createAccount(user, "Checking", "PLN");
    long id = createTx(user, "2024-01-01", 1000, "expense", account);

    // Stale version -> 409.
    mockMvc
        .perform(
            patch("/api/v1/transactions/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":99,\"amountMinor\":1500}"))
        .andExpect(status().isConflict());

    // Correct version -> amount + baseMinor updated.
    mockMvc
        .perform(
            patch("/api/v1/transactions/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"amountMinor\":1500}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amountMinor").value(1500))
        .andExpect(jsonPath("$.baseMinor").value(1500));

    mockMvc
        .perform(
            delete("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNotFound());
  }

  @Test
  void cannotCreateAgainstAnotherUsersAccount() throws Exception {
    RegisteredUser alice = register("tx-alice@example.com", "password123");
    RegisteredUser bob = register("tx-bob@example.com", "password123");
    long aliceAccount = createAccount(alice, "Alice", "PLN");

    // Bob references Alice's account -> 404 (no existence leak).
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2024-01-01\",\"amountMinor\":1000,\"type\":\"expense\",\"accountId\":"
                        + aliceAccount
                        + "}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void usersCannotReachEachOthersTransactions() throws Exception {
    RegisteredUser alice = register("tx-iso-alice@example.com", "password123");
    RegisteredUser bob = register("tx-iso-bob@example.com", "password123");
    long aliceAccount = createAccount(alice, "Alice", "PLN");
    long aliceTx = createTx(alice, "2024-01-01", 1000, "expense", aliceAccount);

    mockMvc
        .perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(jsonPath("$.total").value(0));
    mockMvc
        .perform(
            get("/api/v1/transactions/" + aliceTx).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            patch("/api/v1/transactions/" + aliceTx)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"amountMinor\":1}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/api/v1/transactions/" + aliceTx)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
  }

  private long createAccount(RegisteredUser user, String name, String currency) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\""
                            + name
                            + "\",\"type\":\"checking\",\"currency\":\""
                            + currency
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private long createTx(
      RegisteredUser user, String date, long amountMinor, String type, long accountId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transactions")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"date\":\""
                            + date
                            + "\",\"amountMinor\":"
                            + amountMinor
                            + ",\"type\":\""
                            + type
                            + "\",\"accountId\":"
                            + accountId
                            + "}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
