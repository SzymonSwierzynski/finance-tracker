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
 * Fixed-fixture correctness for the Trends period-comparison: the "previous" period is the
 * immediately-preceding range of equal length; expense movers roll up to top-level parents and are
 * ordered by absolute change; new/gone/uncategorized buckets are represented.
 */
class TrendComparisonTest extends AbstractIntegrationTest {

  @Test
  void previousPeriodIsTheEqualLengthRangeImmediatelyBefore() throws Exception {
    RegisteredUser user = register("tc-basis@example.com", "password123");
    long account = createAccount(user);
    // Selected: Jul 1–30 (30 days) -> previous: Jun 1–30.
    income(user, account, "2026-07-10", 100000);
    expense(user, account, "2026-07-15", 30000, null);
    income(user, account, "2026-06-10", 80000);
    expense(user, account, "2026-06-15", 50000, null);
    expense(user, account, "2026-05-31", 9999, null); // before previous window — excluded
    expense(user, account, "2026-07-31", 9999, null); // after selected window — excluded

    mockMvc
        .perform(
            get("/api/v1/reports/trend-comparison?from=2026-07-01&to=2026-07-30")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("PLN"))
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
  void moversRollUpToParentsOrderedByAbsoluteChangeWithNewGoneAndUncategorized() throws Exception {
    RegisteredUser user = register("tc-movers@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);

    long food = createCategory(user, "Food", "expense", null);
    long restaurants = createCategory(user, "Restaurants", "expense", food);
    long transport = createCategory(user, "Transport", "expense", null);
    long shopping = createCategory(user, "Shopping", "expense", null);
    long entertainment = createCategory(user, "Entertainment", "expense", null);

    // Current period (Jul 1–30):
    expense(user, account, "2026-07-10", 3000, restaurants); // Food = 3000
    expense(user, account, "2026-07-10", 2000, transport); // Transport = 2000
    expense(user, account, "2026-07-10", 300, shopping); // Shopping = 300 (new)
    expense(user, account, "2026-07-10", 700, null); // Uncategorized = 700 (new)
    // Previous period (Jun 1–30):
    expense(user, account, "2026-06-10", 1000, food); // Food direct
    expense(user, account, "2026-06-10", 1000, restaurants); // Food = 2000 total prev
    expense(user, account, "2026-06-10", 2500, transport); // Transport = 2500
    expense(user, account, "2026-06-10", 800, entertainment); // Entertainment = 800 (gone)

    // Deltas: Food +1000, Entertainment -800, Uncategorized +700, Transport -500, Shopping +300.
    mockMvc
        .perform(
            get("/api/v1/reports/trend-comparison?from=2026-07-01&to=2026-07-30")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.current.expenseMinor").value(6000))
        .andExpect(jsonPath("$.previous.expenseMinor").value(5300))
        .andExpect(jsonPath("$.delta.expenseMinor").value(700))
        .andExpect(jsonPath("$.movers.length()").value(5))
        .andExpect(jsonPath("$.movers[0].name").value("Food"))
        .andExpect(jsonPath("$.movers[0].currentMinor").value(3000))
        .andExpect(jsonPath("$.movers[0].previousMinor").value(2000))
        .andExpect(jsonPath("$.movers[0].deltaMinor").value(1000))
        .andExpect(jsonPath("$.movers[1].name").value("Entertainment"))
        .andExpect(jsonPath("$.movers[1].currentMinor").value(0))
        .andExpect(jsonPath("$.movers[1].previousMinor").value(800))
        .andExpect(jsonPath("$.movers[1].deltaMinor").value(-800))
        .andExpect(jsonPath("$.movers[2].name").value("Uncategorized"))
        .andExpect(jsonPath("$.movers[2].categoryId").doesNotExist())
        .andExpect(jsonPath("$.movers[2].currentMinor").value(700))
        .andExpect(jsonPath("$.movers[2].previousMinor").value(0))
        .andExpect(jsonPath("$.movers[3].name").value("Transport"))
        .andExpect(jsonPath("$.movers[3].deltaMinor").value(-500))
        .andExpect(jsonPath("$.movers[4].name").value("Shopping"))
        .andExpect(jsonPath("$.movers[4].currentMinor").value(300))
        .andExpect(jsonPath("$.movers[4].previousMinor").value(0));
  }

  @Test
  void freshUserGetsZeroSummariesAndNoMovers() throws Exception {
    RegisteredUser user = register("tc-empty@example.com", "password123");
    mockMvc
        .perform(
            get("/api/v1/reports/trend-comparison?from=2026-07-01&to=2026-07-30")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.current.expenseMinor").value(0))
        .andExpect(jsonPath("$.previous.expenseMinor").value(0))
        .andExpect(jsonPath("$.movers.length()").value(0));
  }

  @Test
  void rejectsAnInvertedRange() throws Exception {
    RegisteredUser user = register("tc-range@example.com", "password123");
    mockMvc
        .perform(
            get("/api/v1/reports/trend-comparison?from=2026-07-31&to=2026-07-01")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/reports/trend-comparison?from=2026-07-01&to=2026-07-30"))
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

  private long createCategory(RegisteredUser user, String name, String kind, Long parentId)
      throws Exception {
    String parent = parentId == null ? "" : ",\"parentId\":" + parentId;
    return id(
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\"" + parent + "}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void expense(RegisteredUser user, long account, String date, long amount, Long categoryId)
      throws Exception {
    String cat = categoryId == null ? "" : ",\"categoryId\":" + categoryId;
    tx(
        user,
        "{\"date\":\""
            + date
            + "\",\"amountMinor\":"
            + amount
            + ",\"type\":\"expense\",\"accountId\":"
            + account
            + cat
            + "}");
  }

  private void income(RegisteredUser user, long account, String date, long amount)
      throws Exception {
    tx(
        user,
        "{\"date\":\""
            + date
            + "\",\"amountMinor\":"
            + amount
            + ",\"type\":\"income\",\"accountId\":"
            + account
            + "}");
  }

  private void tx(RegisteredUser user, String json) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
