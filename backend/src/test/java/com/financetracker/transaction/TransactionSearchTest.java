package com.financetracker.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Free-text search (?q=) and whitelisted sort (?sort=) on the transaction list. */
class TransactionSearchTest extends AbstractIntegrationTest {

  @Test
  void freeTextSearchMatchesDescriptionCaseInsensitively() throws Exception {
    RegisteredUser user = register("txn-search@example.com", "password123");
    long account = createAccount(user);
    createExpense(user, account, 1000, "Biedronka Warszawa");
    createExpense(user, account, 2000, "Orlen stacja paliw");
    createExpense(user, account, 5000, "Pensja pracodawca");

    mockMvc
        .perform(
            get("/api/v1/transactions?q=orlen").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].amountMinor").value(2000));
    mockMvc
        .perform(
            get("/api/v1/transactions?q=BIEDRONKA").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void sortsByAmountBothDirections() throws Exception {
    RegisteredUser user = register("txn-sort@example.com", "password123");
    long account = createAccount(user);
    createExpense(user, account, 1000, "a");
    createExpense(user, account, 5000, "b");
    createExpense(user, account, 3000, "c");

    mockMvc
        .perform(
            get("/api/v1/transactions?sort=amount,asc")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].amountMinor").value(1000))
        .andExpect(jsonPath("$.items[2].amountMinor").value(5000));
    mockMvc
        .perform(
            get("/api/v1/transactions?sort=amount,desc")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].amountMinor").value(5000));
  }

  @Test
  void rejectsAnUnsortableField() throws Exception {
    RegisteredUser user = register("txn-badsort@example.com", "password123");
    mockMvc
        .perform(
            get("/api/v1/transactions?sort=note").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isUnprocessableEntity());
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

  private long createExpense(RegisteredUser user, long account, long amount, String description)
      throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/transactions")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"date\":\"2026-05-10\",\"amountMinor\":"
                            + amount
                            + ",\"type\":\"expense\",\"accountId\":"
                            + account
                            + ",\"description\":\""
                            + description
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
