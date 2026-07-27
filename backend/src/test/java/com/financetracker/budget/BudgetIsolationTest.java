package com.financetracker.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Budgets: CRUD, month progress to the grosz (base currency), subcategory roll-up into a parent
 * budget, expense-only + one-per-category invariants, and cross-user isolation. Progress tests use
 * a fixed past month so the numbers are deterministic regardless of "today".
 */
class BudgetIsolationTest extends AbstractIntegrationTest {

  @Test
  void createsListsUpdatesDeletes() throws Exception {
    RegisteredUser user = register("budget-crud@example.com", "password123");
    clearCategories(user);
    long groceries = createCategory(user, "Groceries", "expense");

    long id = createBudget(user, groceries, 100000);
    mockMvc
        .perform(get("/api/v1/budgets").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].categoryId").value(groceries))
        .andExpect(jsonPath("$.items[0].categoryName").value("Groceries"))
        .andExpect(jsonPath("$.items[0].amountMinor").value(100000))
        .andExpect(jsonPath("$.items[0].spentMinor").value(0))
        .andExpect(jsonPath("$.items[0].remainingMinor").value(100000))
        .andExpect(jsonPath("$.items[0].over").value(false))
        .andExpect(jsonPath("$.items[0].version").value(0));

    mockMvc
        .perform(
            patch("/api/v1/budgets/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":120000,\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amountMinor").value(120000))
        .andExpect(jsonPath("$.version").value(1));

    mockMvc
        .perform(delete("/api/v1/budgets/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/budgets").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void tracksSpendAgainstTheLimitForTheMonth() throws Exception {
    RegisteredUser user = register("budget-progress@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    createBudget(user, food, 50000);

    createExpense(user, account, "2026-03-05", 20000, food);
    createExpense(user, account, "2026-03-20", 15000, food);
    createExpense(user, account, "2026-02-10", 99999, food); // another month — must not count

    mockMvc
        .perform(
            get("/api/v1/budgets?month=2026-03").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.month").value("2026-03"))
        .andExpect(jsonPath("$.currency").value("PLN"))
        .andExpect(jsonPath("$.items[0].spentMinor").value(35000))
        .andExpect(jsonPath("$.items[0].remainingMinor").value(15000))
        .andExpect(jsonPath("$.items[0].over").value(false));

    createExpense(user, account, "2026-03-25", 20000, food); // now 55000 > 50000
    mockMvc
        .perform(
            get("/api/v1/budgets?month=2026-03").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].spentMinor").value(55000))
        .andExpect(jsonPath("$.items[0].remainingMinor").value(-5000))
        .andExpect(jsonPath("$.items[0].over").value(true));
  }

  @Test
  void rollsSubcategorySpendIntoTheParentBudget() throws Exception {
    RegisteredUser user = register("budget-rollup@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense");
    long restaurants = createSubcategory(user, "Restaurants", "expense", food);
    createBudget(user, food, 100000);

    createExpense(user, account, "2026-04-10", 30000, restaurants); // subcategory
    createExpense(user, account, "2026-04-15", 10000, food); // parent directly

    mockMvc
        .perform(
            get("/api/v1/budgets?month=2026-04").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].categoryId").value(food))
        .andExpect(jsonPath("$.items[0].spentMinor").value(40000))
        .andExpect(jsonPath("$.items[0].remainingMinor").value(60000));
  }

  @Test
  void rejectsIncomeCategoryAndDuplicateBudget() throws Exception {
    RegisteredUser user = register("budget-invalid@example.com", "password123");
    clearCategories(user);
    long salary = createCategory(user, "Salary", "income");
    long rent = createCategory(user, "Rent", "expense");

    mockMvc
        .perform(
            post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + salary + ",\"amountMinor\":1000}"))
        .andExpect(status().isUnprocessableEntity());

    createBudget(user, rent, 150000);
    mockMvc
        .perform(
            post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + rent + ",\"amountMinor\":150000}"))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsInvalidInput() throws Exception {
    RegisteredUser user = register("budget-validation@example.com", "password123");
    mockMvc
        .perform(
            post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":1,\"amountMinor\":0}"))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(
            post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":1000}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void usersCannotReachEachOthersBudgets() throws Exception {
    RegisteredUser alice = register("budget-a@example.com", "password123");
    RegisteredUser bob = register("budget-b@example.com", "password123");
    clearCategories(alice);
    long aliceCategory = createCategory(alice, "Groceries", "expense");
    long aliceBudget = createBudget(alice, aliceCategory, 100000);

    mockMvc
        .perform(get("/api/v1/budgets").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    mockMvc
        .perform(
            patch("/api/v1/budgets/" + aliceBudget)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":1,\"version\":0}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/api/v1/budgets/" + aliceBudget).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/budgets")).andExpect(status().isUnauthorized());
  }

  @Test
  void persistsAndEchoesTheRolloverFlag() throws Exception {
    RegisteredUser user = register("budget-rollover-flag@example.com", "password123");
    clearCategories(user);
    long food = createCategory(user, "Food", "expense");

    // Explicit rollover=true on create is stored and echoed on the create response…
    long id =
        id(
            mockMvc
                .perform(
                    post("/api/v1/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"categoryId\":"
                                + food
                                + ",\"amountMinor\":50000,\"rollover\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rollover").value(true))
                .andReturn());

    // …and on the monthly progress list, with carriedInMinor present (0 for now).
    mockMvc
        .perform(get("/api/v1/budgets").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].rollover").value(true))
        .andExpect(jsonPath("$.items[0].carriedInMinor").value(0));

    // Omitting rollover defaults to false (a new category so no duplicate-budget conflict).
    long rent = createCategory(user, "Rent", "expense");
    mockMvc
        .perform(
            post("/api/v1/budgets")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + rent + ",\"amountMinor\":100000}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rollover").value(false));

    // Update can toggle rollover off (send the current version).
    mockMvc
        .perform(
            patch("/api/v1/budgets/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":50000,\"version\":0,\"rollover\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rollover").value(false));
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
