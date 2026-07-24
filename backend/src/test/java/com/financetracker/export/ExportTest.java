package com.financetracker.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        .contains(
            "date,type,amountMinor,currency,rateToBase,account,counterAccount,category,categoryParent,description,note")
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

  @Test
  void restoreRoundTripsTransfers() throws Exception {
    RegisteredUser source = register("restore-xfer-src@example.com", "password123");
    clearCategories(source);
    long checking = createAccount(source, "Checking");
    long savings = createAccount(source, "Savings");
    createTransfer(source, checking, savings, "2026-06-01", 50000);
    String backup =
        mockMvc
            .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(source)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    RegisteredUser target = register("restore-xfer-dst@example.com", "password123");
    clearCategories(target);
    mockMvc
        .perform(
            post("/api/v1/export/restore")
                .header(HttpHeaders.AUTHORIZATION, bearer(target))
                .contentType(MediaType.APPLICATION_JSON)
                .content(backup))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountsCreated").value(2))
        .andExpect(jsonPath("$.transactionsImported").value(1))
        .andExpect(jsonPath("$.transfersSkipped").value(0));

    // The transfer round-trips with both sides intact (account + counter-account by name).
    mockMvc
        .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(target)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions.length()").value(1))
        .andExpect(jsonPath("$.transactions[0].type").value("transfer"))
        .andExpect(jsonPath("$.transactions[0].amountMinor").value(50000))
        .andExpect(jsonPath("$.transactions[0].account").value("Checking"))
        .andExpect(jsonPath("$.transactions[0].counterAccount").value("Savings"));
  }

  @Test
  void restorePreservesCategoryUnderDuplicateLeafNames() throws Exception {
    RegisteredUser source = register("restore-dupcat-src@example.com", "password123");
    clearCategories(source);
    long account = createAccount(source);
    long foodOther = createSubcategory(source, "Other", createCategory(source, "Food"));
    long transportOther = createSubcategory(source, "Other", createCategory(source, "Transport"));
    createExpense(source, account, "2026-05-01", 1000, foodOther, "groceries");
    createExpense(source, account, "2026-05-02", 2000, transportOther, "bus");
    String backup = backupOf(source);

    RegisteredUser target = register("restore-dupcat-dst@example.com", "password123");
    clearCategories(target);
    mockMvc
        .perform(
            post("/api/v1/export/restore")
                .header(HttpHeaders.AUTHORIZATION, bearer(target))
                .contentType(MediaType.APPLICATION_JSON)
                .content(backup))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionsImported").value(2));

    // Re-export: each "Other" transaction keeps its correct parent (not collapsed to one).
    JsonNode txns = objectMapper.readTree(backupOf(target)).get("transactions");
    assertThat(txns).hasSize(2);
    for (JsonNode tx : txns) {
      assertThat(tx.get("category").asText()).isEqualTo("Other");
      assertThat(tx.get("categoryParent").asText())
          .isEqualTo(tx.get("amountMinor").asLong() == 1000 ? "Food" : "Transport");
    }
  }

  @Test
  void usersCannotExportEachOthersData() throws Exception {
    RegisteredUser alice = register("export-iso-a@example.com", "password123");
    RegisteredUser bob = register("export-iso-b@example.com", "password123");
    clearCategories(bob);
    long bobAccount = createAccount(bob);
    createExpense(bob, bobAccount, "2026-05-10", 5000, createCategory(bob, "Groceries"), "bob's");

    // Alice has no transactions/accounts; her export must not leak Bob's.
    mockMvc
        .perform(
            get("/api/v1/export/transactions").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions.length()").value(0))
        .andExpect(jsonPath("$.accounts.length()").value(0));
  }

  @Test
  void csvExportNeutralizesFormulaInjection() throws Exception {
    RegisteredUser user = register("csv-inj@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    createExpense(user, account, "2026-05-10", 1000, createCategory(user, "Groceries"), "=1+2");

    String csv =
        mockMvc
            .perform(
                get("/api/v1/export/transactions/csv")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // The leading '=' is prefixed with a single quote so a spreadsheet treats the cell as text.
    assertThat(csv).contains("'=1+2");
  }

  @Test
  void restoreRejectsAMalformedBackupWith422() throws Exception {
    RegisteredUser user = register("restore-bad@example.com", "password123");
    // "spaceship" is not a valid AccountType — a bad value should be 422, not a 500.
    String badBackup =
        "{\"reportingCurrency\":\"PLN\",\"accounts\":"
            + "[{\"name\":\"X\",\"type\":\"spaceship\",\"currency\":\"PLN\"}],"
            + "\"categories\":[],\"transactions\":[]}";
    mockMvc
        .perform(
            post("/api/v1/export/restore")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(badBackup))
        .andExpect(status().isUnprocessableEntity());
  }

  private String backupOf(RegisteredUser user) throws Exception {
    return mockMvc
        .perform(get("/api/v1/export/backup").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private long createSubcategory(RegisteredUser user, String name, long parentId) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\""
                            + name
                            + "\",\"kind\":\"expense\",\"parentId\":"
                            + parentId
                            + "}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long createAccount(RegisteredUser user) throws Exception {
    return createAccount(user, "Checking");
  }

  private long createAccount(RegisteredUser user, String name) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"" + name + "\",\"type\":\"checking\",\"currency\":\"PLN\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void createTransfer(RegisteredUser user, long from, long to, String date, long amount)
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
                        + ",\"type\":\"transfer\",\"accountId\":"
                        + from
                        + ",\"counterAccountId\":"
                        + to
                        + "}"))
        .andExpect(status().isCreated());
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
