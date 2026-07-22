package com.financetracker.reporting;

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
 * Fixed-fixture correctness for spending-over-time: the monthly trend zero-fills empty periods, and
 * cashflow accumulates a running net — all in base-currency minor units.
 */
class TrendCashflowTest extends AbstractIntegrationTest {

  @Test
  void monthlyTrendZeroFillsEmptyPeriods() throws Exception {
    RegisteredUser user = register("trend@example.com", "password123");
    long account = createAccount(user);
    expense(user, account, "2026-05-10", 1000);
    income(user, account, "2026-05-20", 5000);
    expense(user, account, "2026-06-15", 3000);
    income(user, account, "2026-06-16", 2000);
    expense(user, account, "2026-07-01", 500);

    // Range starts in April (no activity) to prove the empty bucket is zero-filled.
    mockMvc
        .perform(
            get("/api/v1/reports/trend?from=2026-04-01&to=2026-07-31&interval=month")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interval").value("month"))
        .andExpect(jsonPath("$.buckets.length()").value(4))
        .andExpect(jsonPath("$.buckets[0].period").value("2026-04"))
        .andExpect(jsonPath("$.buckets[0].incomeMinor").value(0))
        .andExpect(jsonPath("$.buckets[0].expenseMinor").value(0))
        .andExpect(jsonPath("$.buckets[1].period").value("2026-05"))
        .andExpect(jsonPath("$.buckets[1].incomeMinor").value(5000))
        .andExpect(jsonPath("$.buckets[1].expenseMinor").value(1000))
        .andExpect(jsonPath("$.buckets[2].period").value("2026-06"))
        .andExpect(jsonPath("$.buckets[2].incomeMinor").value(2000))
        .andExpect(jsonPath("$.buckets[2].expenseMinor").value(3000))
        .andExpect(jsonPath("$.buckets[3].period").value("2026-07"))
        .andExpect(jsonPath("$.buckets[3].expenseMinor").value(500));
  }

  @Test
  void cashflowAccumulatesRunningNet() throws Exception {
    RegisteredUser user = register("cashflow@example.com", "password123");
    long account = createAccount(user);
    income(user, account, "2026-05-20", 5000);
    expense(user, account, "2026-05-10", 1000);
    expense(user, account, "2026-06-15", 3000);
    income(user, account, "2026-06-16", 2000);

    mockMvc
        .perform(
            get("/api/v1/reports/cashflow?from=2026-05-01&to=2026-06-30")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.buckets.length()").value(2))
        .andExpect(jsonPath("$.buckets[0].period").value("2026-05"))
        .andExpect(jsonPath("$.buckets[0].netMinor").value(4000)) // 5000 - 1000
        .andExpect(jsonPath("$.buckets[0].runningNetMinor").value(4000))
        .andExpect(jsonPath("$.buckets[1].period").value("2026-06"))
        .andExpect(jsonPath("$.buckets[1].netMinor").value(-1000)) // 2000 - 3000
        .andExpect(jsonPath("$.buckets[1].runningNetMinor").value(3000)); // 4000 + (-1000)
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

  private void expense(RegisteredUser user, long account, String date, long amount)
      throws Exception {
    tx(user, account, date, amount, "expense");
  }

  private void income(RegisteredUser user, long account, String date, long amount)
      throws Exception {
    tx(user, account, date, amount, "income");
  }

  private void tx(RegisteredUser user, long account, String date, long amount, String type)
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
                        + ",\"type\":\""
                        + type
                        + "\",\"accountId\":"
                        + account
                        + "}"))
        .andExpect(status().isCreated());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
