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
 * Data export and restore: the user's transactions as JSON rows and as CSV, the full-data backup,
 * and a backup → restore round-trip (money stays integer minor units throughout).
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
  void backupContainsReportingCurrencyAccountsCategoriesAndTransactions() throws Exception {
    RegisteredUser user = register("backup@example.com", "password123");
    clearCategories(user);
    long groceries = createCategory(user, "Groceries");
    long account = createAccount(user);
    createExpense(user, account, "2026-05-10", 1999, groceries, "Biedronka");

    mockMvc
        .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportingCurrency").value("PLN"))
        .andExpect(jsonPath("$.accounts.length()").value(1))
        .andExpect(jsonPath("$.accounts[0].name").value("Checking"))
        .andExpect(jsonPath("$.accounts[0].type").value("checking"))
        .andExpect(jsonPath("$.accounts[0].currency").value("PLN"))
        .andExpect(jsonPath("$.categories.length()").value(1))
        .andExpect(jsonPath("$.categories[0].name").value("Groceries"))
        .andExpect(jsonPath("$.categories[0].kind").value("expense"))
        .andExpect(jsonPath("$.transactions.length()").value(1))
        .andExpect(jsonPath("$.transactions[0].amountMinor").value(1999))
        .andExpect(jsonPath("$.transactions[0].category").value("Groceries"));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/export/transactions")).andExpect(status().isUnauthorized());
  }

  @Test
  void restoreRoundTripsABackupAndIsIdempotent() throws Exception {
    RegisteredUser source = register("restore-src@example.com", "password123");
    clearCategories(source);
    long groceries = createCategory(source, "Groceries");
    long account = createAccount(source);
    createExpense(source, account, "2026-05-10", 1999, groceries, "Biedronka");
    String backup =
        mockMvc
            .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(source)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Restore into a fresh user with a clean slate: everything is created once.
    RegisteredUser target = register("restore-dst@example.com", "password123");
    clearCategories(target);
    mockMvc
        .perform(
            post("/api/v1/export/restore")
                .header(HttpHeaders.AUTHORIZATION, bearer(target))
                .contentType(MediaType.APPLICATION_JSON)
                .content(backup))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountsCreated").value(1))
        .andExpect(jsonPath("$.categoriesCreated").value(1))
        .andExpect(jsonPath("$.transactionsImported").value(1))
        .andExpect(jsonPath("$.transactionsSkipped").value(0))
        .andExpect(jsonPath("$.transfersSkipped").value(0));

    // The target now backs up to the same data — a lossless round-trip (money as integer minor
    // units, the locked rate preserved).
    mockMvc
        .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(target)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportingCurrency").value("PLN"))
        .andExpect(jsonPath("$.accounts[0].name").value("Checking"))
        .andExpect(jsonPath("$.categories[0].name").value("Groceries"))
        .andExpect(jsonPath("$.transactions.length()").value(1))
        .andExpect(jsonPath("$.transactions[0].amountMinor").value(1999))
        .andExpect(jsonPath("$.transactions[0].account").value("Checking"))
        .andExpect(jsonPath("$.transactions[0].category").value("Groceries"))
        .andExpect(jsonPath("$.transactions[0].description").value("Biedronka"));

    // Idempotent: the same backup a second time creates and imports nothing new.
    mockMvc
        .perform(
            post("/api/v1/export/restore")
                .header(HttpHeaders.AUTHORIZATION, bearer(target))
                .contentType(MediaType.APPLICATION_JSON)
                .content(backup))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountsCreated").value(0))
        .andExpect(jsonPath("$.categoriesCreated").value(0))
        .andExpect(jsonPath("$.transactionsImported").value(0))
        .andExpect(jsonPath("$.transactionsSkipped").value(1));
    mockMvc
        .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(target)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions.length()").value(1));
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
