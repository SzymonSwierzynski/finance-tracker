package com.financetracker.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Budget rollover end-to-end: the floored envelope carry (design §2), off-budget parity, and
 * subcategory roll-up into a parent's carry. The anchor is the freshly-created budget's creation
 * month (≈ now), so the fixture uses now-relative months — a fixed past month would sit before
 * creation and never fold. Cross-user isolation is already covered by {@link BudgetIsolationTest}.
 */
class BudgetRolloverTest extends AbstractIntegrationTest {

  @Test
  void carriesUnspentBudgetForwardWithFloor() throws Exception {
    RegisteredUser user = register("rollover-carry@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    createRolloverBudget(user, food, 50000); // limit 500.00, rollover on, created this month

    YearMonth base = YearMonth.now(ZoneOffset.UTC); // = creation month
    createExpense(user, account, base.atDay(5).toString(), 40000, food); // base:   400
    createExpense(user, account, base.plusMonths(1).atDay(5).toString(), 55000, food); // +1: 550
    createExpense(user, account, base.plusMonths(2).atDay(5).toString(), 62000, food); // +2: 620
    createExpense(user, account, base.plusMonths(3).atDay(5).toString(), 30000, food); // +3: 300

    // month, carriedIn, spent, remaining, over  (amount is always 50000; available =
    // 50000+carriedIn)
    assertMonth(user, base, 0, 40000, 10000, false); // avail 500, spent 400
    assertMonth(user, base.plusMonths(1), 10000, 55000, 5000, false); // avail 600, spent 550
    assertMonth(user, base.plusMonths(2), 5000, 62000, -7000, true); // avail 550, spent 620 → over
    assertMonth(user, base.plusMonths(3), 0, 30000, 20000, false); // carry floored → avail 500
  }

  @Test
  void rolloverOffIgnoresPriorMonths() throws Exception {
    RegisteredUser user = register("rollover-off@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    createBudget(user, food, 50000); // rollover defaults false

    YearMonth base = YearMonth.now(ZoneOffset.UTC);
    createExpense(user, account, base.atDay(5).toString(), 10000, food); // underspend this month

    // Next month starts fresh — no carry, exactly as before.
    mockMvc
        .perform(
            get("/api/v1/budgets?month=" + base.plusMonths(1))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].rollover").value(false))
        .andExpect(jsonPath("$.items[0].carriedInMinor").value(0))
        .andExpect(jsonPath("$.items[0].amountMinor").value(50000))
        .andExpect(jsonPath("$.items[0].spentMinor").value(0))
        .andExpect(jsonPath("$.items[0].remainingMinor").value(50000))
        .andExpect(jsonPath("$.items[0].over").value(false));
  }

  @Test
  void foldsSubcategorySpendIntoParentCarry() throws Exception {
    RegisteredUser user = register("rollover-subcat@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    long restaurants = createSubcategory(user, "Restaurants", "expense", food);
    createRolloverBudget(user, food, 50000);

    YearMonth base = YearMonth.now(ZoneOffset.UTC);
    createExpense(user, account, base.atDay(5).toString(), 30000, restaurants); // rolls to parent

    // Parent carry = 50000 - 30000 = 20000 → next month available 70000.
    assertMonth(user, base.plusMonths(1), 20000, 0, 70000, false);
  }

  private void assertMonth(
      RegisteredUser user,
      YearMonth month,
      long carriedIn,
      long spent,
      long remaining,
      boolean over)
      throws Exception {
    mockMvc
        .perform(
            get("/api/v1/budgets?month=" + month).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].rollover").value(true))
        .andExpect(jsonPath("$.items[0].amountMinor").value(50000))
        .andExpect(jsonPath("$.items[0].carriedInMinor").value(carriedIn))
        .andExpect(jsonPath("$.items[0].spentMinor").value(spent))
        .andExpect(jsonPath("$.items[0].remainingMinor").value(remaining))
        .andExpect(jsonPath("$.items[0].over").value(over));
  }

  private long createRolloverBudget(RegisteredUser user, long categoryId, long amountMinor)
      throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/budgets")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"categoryId\":"
                            + categoryId
                            + ",\"amountMinor\":"
                            + amountMinor
                            + ",\"rollover\":true}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long createBudget(RegisteredUser user, long categoryId, long amountMinor)
      throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/budgets")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"categoryId\":" + categoryId + ",\"amountMinor\":" + amountMinor + "}"))
            .andExpect(status().isCreated())
            .andReturn());
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

  private long createSubcategory(RegisteredUser user, String name, String kind, long parentId)
      throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\""
                            + name
                            + "\",\"kind\":\""
                            + kind
                            + "\",\"parentId\":"
                            + parentId
                            + "}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void createExpense(
      RegisteredUser user, long account, String date, long amount, long categoryId)
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
                        + "}"))
        .andExpect(status().isCreated());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
