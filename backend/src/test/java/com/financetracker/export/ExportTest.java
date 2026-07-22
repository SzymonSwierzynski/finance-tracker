package com.financetracker.export;

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

/**
 * Data export: the user's transactions as JSON rows and as CSV (money stays integer minor units).
 */
class ExportTest extends AbstractIntegrationTest {

  @Test
  void exportsTransactionsAsJsonAndCsv() throws Exception {
    RegisteredUser user = register("export@example.com", "password123");
    clearCategories(user);
    long groceries = createCategory(user, "Groceries");
    long account = createAccount(user);
    createExpense(user, account, "2026-05-10", 1999, groceries, "Biedronka");

    mockMvc
        .perform(get("/api/v1/export/transactions").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].amountMinor").value(1999))
        .andExpect(jsonPath("$[0].account").value("Checking"))
        .andExpect(jsonPath("$[0].category").value("Groceries"))
        .andExpect(jsonPath("$[0].description").value("Biedronka"));

    MvcResult csv =
        mockMvc
            .perform(
                get("/api/v1/export/transactions/csv")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    String body = csv.getResponse().getContentAsString();
    assertThat(body)
        .contains("date,type,amountMinor,currency,rateToBase,account,category,description,note")
        .contains("2026-05-10")
        .contains("Biedronka")
        .contains("1999");
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/export/transactions")).andExpect(status().isUnauthorized());
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

  private long createCategory(RegisteredUser user, String name) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"kind\":\"expense\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void createExpense(
      RegisteredUser user, long account, String date, long amount, long categoryId, String desc)
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
                        + ",\"description\":\""
                        + desc
                        + "\"}"))
        .andExpect(status().isCreated());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
