package com.financetracker.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Fixed-fixture correctness for the period summary, asserted to the grosz. Exercises base-currency
 * conversion of foreign amounts via their locked rates (including a half-up rounding case),
 * exclusion of transfers and out-of-range rows, and per-user isolation.
 */
class ReportingSummaryTest extends AbstractIntegrationTest {

  @Test
  void summaryFoldsToBaseCurrencyToTheGrosz() throws Exception {
    RegisteredUser user = register("report-user@example.com", "password123");
    long checking = createAccount(user, "Checking");
    long savings = createAccount(user, "Savings");

    // --- May fixture (base currency PLN; reporting range 2024-05-01..2024-05-31) ---
    income(user, "2024-05-05", 100000, checking); // +1000.00 PLN
    expense(user, "2024-05-10", 25050, checking); // 250.50 PLN
    expenseForeign(user, "2024-05-15", 10000, "EUR", "4.30", checking); // 100.00 EUR -> 430.00 PLN
    expenseForeign(
        user, "2024-05-18", 4500, "USD", "1.115", checking); // 4500*1.115=5017.5 -> 5018 (half-up)
    transfer(user, "2024-05-20", 5000, checking, savings); // excluded from income/expense
    income(user, "2024-04-30", 7777, checking); // out of range -> excluded

    // income = 100000
    // expense = 25050 + 43000 + 5018 = 73068
    // net = 26932
    mockMvc
        .perform(
            get("/api/v1/reports/summary?from=2024-05-01&to=2024-05-31")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("PLN"))
        .andExpect(jsonPath("$.incomeMinor").value(100000))
        .andExpect(jsonPath("$.expenseMinor").value(73068))
        .andExpect(jsonPath("$.netMinor").value(26932));

    // Empty range -> zeros.
    mockMvc
        .perform(
            get("/api/v1/reports/summary?from=2024-07-01&to=2024-07-31")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(jsonPath("$.incomeMinor").value(0))
        .andExpect(jsonPath("$.expenseMinor").value(0))
        .andExpect(jsonPath("$.netMinor").value(0));
  }

  @Test
  void fromAfterToIsRejected() throws Exception {
    RegisteredUser user = register("report-range@example.com", "password123");
    mockMvc
        .perform(
            get("/api/v1/reports/summary?from=2024-05-31&to=2024-05-01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void summaryIsScopedToTheUser() throws Exception {
    RegisteredUser alice = register("report-alice@example.com", "password123");
    RegisteredUser bob = register("report-bob@example.com", "password123");
    long aliceChecking = createAccount(alice, "Alice");
    income(alice, "2024-05-05", 100000, aliceChecking);

    // Bob's summary over the same range sees none of Alice's money.
    mockMvc
        .perform(
            get("/api/v1/reports/summary?from=2024-05-01&to=2024-05-31")
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incomeMinor").value(0))
        .andExpect(jsonPath("$.netMinor").value(0));
  }

  private long createAccount(RegisteredUser user, String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"" + name + "\",\"type\":\"checking\",\"currency\":\"PLN\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private void income(RegisteredUser user, String date, long amount, long account)
      throws Exception {
    create(
        user,
        "{\"date\":\""
            + date
            + "\",\"amountMinor\":"
            + amount
            + ",\"type\":\"income\",\"accountId\":"
            + account
            + "}");
  }

  private void expense(RegisteredUser user, String date, long amount, long account)
      throws Exception {
    create(
        user,
        "{\"date\":\""
            + date
            + "\",\"amountMinor\":"
            + amount
            + ",\"type\":\"expense\",\"accountId\":"
            + account
            + "}");
  }

  private void expenseForeign(
      RegisteredUser user, String date, long amount, String currency, String rate, long account)
      throws Exception {
    create(
        user,
        "{\"date\":\""
            + date
            + "\",\"amountMinor\":"
            + amount
            + ",\"type\":\"expense\",\"accountId\":"
            + account
            + ",\"currency\":\""
            + currency
            + "\",\"rateToBase\":"
            + rate
            + "}");
  }

  private void transfer(RegisteredUser user, String date, long amount, long from, long to)
      throws Exception {
    create(
        user,
        "{\"date\":\""
            + date
            + "\",\"amountMinor\":"
            + amount
            + ",\"type\":\"transfer\",\"accountId\":"
            + from
            + ",\"counterAccountId\":"
            + to
            + "}");
  }

  private void create(RegisteredUser user, String json) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());
  }
}
