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
 * Period-over-period comparison (month-over-month / year-over-year). Fixed fixtures across three
 * months make the current/previous/delta totals deterministic to the grosz; the previous period is
 * the requested range shifted back one month or one year.
 */
class ComparisonTest extends AbstractIntegrationTest {

  @Test
  void monthOverMonthComparesAgainstThePreviousMonth() throws Exception {
    RegisteredUser user = register("cmp-mom@example.com", "password123");
    long account = createAccount(user);
    // Current month (Jul 2026), previous month (Jun 2026), and a year earlier (Jul 2025).
    createTxn(user, account, "2026-07-10", 100000, "income");
    createTxn(user, account, "2026-07-15", 30000, "expense");
    createTxn(user, account, "2026-06-10", 80000, "income");
    createTxn(user, account, "2026-06-15", 50000, "expense");
    createTxn(user, account, "2025-07-10", 40000, "income");
    createTxn(user, account, "2025-07-15", 20000, "expense");

    mockMvc
        .perform(
            get("/api/v1/reports/comparison?from=2026-07-01&to=2026-07-31&mode=month")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("PLN"))
        .andExpect(jsonPath("$.mode").value("month"))
        .andExpect(jsonPath("$.current.incomeMinor").value(100000))
        .andExpect(jsonPath("$.current.expenseMinor").value(30000))
        .andExpect(jsonPath("$.current.netMinor").value(70000))
        .andExpect(jsonPath("$.previous.from").value("2026-06-01"))
        .andExpect(jsonPath("$.previous.to").value("2026-06-30"))
        .andExpect(jsonPath("$.previous.incomeMinor").value(80000))
        .andExpect(jsonPath("$.previous.expenseMinor").value(50000))
        .andExpect(jsonPath("$.previous.netMinor").value(30000))
        .andExpect(jsonPath("$.delta.incomeMinor").value(20000))
        .andExpect(jsonPath("$.delta.expenseMinor").value(-20000))
        .andExpect(jsonPath("$.delta.netMinor").value(40000));
  }

  @Test
  void yearOverYearComparesAgainstTheSameRangeLastYear() throws Exception {
    RegisteredUser user = register("cmp-yoy@example.com", "password123");
    long account = createAccount(user);
    createTxn(user, account, "2026-07-10", 100000, "income");
    createTxn(user, account, "2026-07-15", 30000, "expense");
    createTxn(user, account, "2026-06-10", 80000, "income"); // must NOT count for YoY
    createTxn(user, account, "2025-07-10", 40000, "income");
    createTxn(user, account, "2025-07-15", 20000, "expense");

    mockMvc
        .perform(
            get("/api/v1/reports/comparison?from=2026-07-01&to=2026-07-31&mode=year")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("year"))
        .andExpect(jsonPath("$.current.netMinor").value(70000))
        .andExpect(jsonPath("$.previous.from").value("2025-07-01"))
        .andExpect(jsonPath("$.previous.to").value("2025-07-31"))
        .andExpect(jsonPath("$.previous.incomeMinor").value(40000))
        .andExpect(jsonPath("$.previous.expenseMinor").value(20000))
        .andExpect(jsonPath("$.previous.netMinor").value(20000))
        .andExpect(jsonPath("$.delta.incomeMinor").value(60000))
        .andExpect(jsonPath("$.delta.expenseMinor").value(10000))
        .andExpect(jsonPath("$.delta.netMinor").value(50000));
  }

  @Test
  void rejectsAnUnknownMode() throws Exception {
    RegisteredUser user = register("cmp-mode@example.com", "password123");
    mockMvc
        .perform(
            get("/api/v1/reports/comparison?from=2026-07-01&to=2026-07-31&mode=decade")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/reports/comparison?from=2026-07-01&to=2026-07-31"))
        .andExpect(status().isUnauthorized());
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

  private void createTxn(RegisteredUser user, long account, String date, long amount, String type)
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
