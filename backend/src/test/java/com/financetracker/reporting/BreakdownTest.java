package com.financetracker.reporting;

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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Fixed-fixture correctness for the category breakdown: subcategory roll-up to parent, the
 * synthetic "(direct)" slice, the Uncategorized bucket, base-currency (FX) conversion, and the kind
 * filter.
 */
class BreakdownTest extends AbstractIntegrationTest {

  @Test
  void rollsUpToParentsWithDirectSliceAndUncategorized() throws Exception {
    RegisteredUser user = register("breakdown@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);

    long food = createCategory(user, "Food", "expense", null);
    long groceries = createCategory(user, "Groceries", "expense", food);
    long restaurants = createCategory(user, "Restaurants", "expense", food);
    long transport = createCategory(user, "Transport", "expense", null);

    expense(user, account, 1000, food); // direct on the parent
    expense(user, account, 5000, groceries);
    expense(user, account, 3000, restaurants);
    expense(user, account, 2000, transport);
    expenseForeign(user, account, 1000, "EUR", "4.30", transport); // 100.00 EUR -> 430.00 base
    expense(user, account, 1500, null); // uncategorized
    income(user, account, 7000); // excluded from the expense breakdown

    ResultActions res =
        mockMvc
            .perform(
                get("/api/v1/reports/breakdown?from=2024-05-01&to=2024-05-31&kind=expense")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("expense"))
            .andExpect(jsonPath("$.currency").value("PLN"))
            // 9000 (Food) + 6300 (Transport) + 1500 (Uncategorized)
            .andExpect(jsonPath("$.totalBaseMinor").value(16800))
            .andExpect(jsonPath("$.parents.length()").value(3));

    // Parent 0: Food = 1000 direct + 5000 + 3000 = 9000, with three child slices.
    res.andExpect(jsonPath("$.parents[0].name").value("Food"))
        .andExpect(jsonPath("$.parents[0].baseMinor").value(9000))
        .andExpect(jsonPath("$.parents[0].children.length()").value(3))
        .andExpect(jsonPath("$.parents[0].children[0].name").value("Groceries"))
        .andExpect(jsonPath("$.parents[0].children[0].baseMinor").value(5000))
        .andExpect(jsonPath("$.parents[0].children[2].name").value("Food (direct)"))
        .andExpect(jsonPath("$.parents[0].children[2].baseMinor").value(1000));

    // Parent 1: Transport = 2000 + 4300 (FX) = 6300, no subcategories.
    res.andExpect(jsonPath("$.parents[1].name").value("Transport"))
        .andExpect(jsonPath("$.parents[1].baseMinor").value(6300))
        .andExpect(jsonPath("$.parents[1].children.length()").value(0));

    // Parent 2: Uncategorized bucket (null category) = 1500.
    res.andExpect(jsonPath("$.parents[2].name").value("Uncategorized"))
        .andExpect(jsonPath("$.parents[2].baseMinor").value(1500))
        .andExpect(jsonPath("$.parents[2].categoryId").doesNotExist());
  }

  @Test
  void parentIdFiltersToASingleParent() throws Exception {
    RegisteredUser user = register("breakdown-drill@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", "expense", null);
    long groceries = createCategory(user, "Groceries", "expense", food);
    long transport = createCategory(user, "Transport", "expense", null);
    expense(user, account, 5000, groceries);
    expense(user, account, 2000, transport);

    mockMvc
        .perform(
            get("/api/v1/reports/breakdown?from=2024-05-01&to=2024-05-31&kind=expense&parentId="
                    + food)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parents.length()").value(1))
        .andExpect(jsonPath("$.parents[0].name").value("Food"))
        .andExpect(jsonPath("$.parents[0].children[0].name").value("Groceries"));
  }

  @Test
  void incomeBreakdownIsSeparateFromExpense() throws Exception {
    RegisteredUser user = register("breakdown-income@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long salary = createCategory(user, "Salary", "income", null);
    incomeCategorized(user, account, 7000, salary);
    expense(user, account, 1000, null);

    mockMvc
        .perform(
            get("/api/v1/reports/breakdown?from=2024-05-01&to=2024-05-31&kind=income")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalBaseMinor").value(7000))
        .andExpect(jsonPath("$.parents[0].name").value("Salary"))
        .andExpect(jsonPath("$.parents[0].baseMinor").value(7000));
  }

  @Test
  void breakdownIsScopedToTheUser() throws Exception {
    RegisteredUser alice = register("breakdown-alice@example.com", "password123");
    RegisteredUser bob = register("breakdown-bob@example.com", "password123");
    long aliceAccount = createAccount(alice);
    long cat = createCategory(alice, "Food", "expense", null);
    expense(alice, aliceAccount, 5000, cat);

    mockMvc
        .perform(
            get("/api/v1/reports/breakdown?from=2024-05-01&to=2024-05-31&kind=expense")
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalBaseMinor").value(0))
        .andExpect(jsonPath("$.parents.length()").value(0));
  }

  // --- helpers ---

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
    String body =
        parentId == null
            ? "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\"}"
            : "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"parentId\":" + parentId + "}";
    return id(
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void expense(RegisteredUser user, long account, long amount, Long categoryId)
      throws Exception {
    String cat = categoryId == null ? "" : ",\"categoryId\":" + categoryId;
    tx(
        user,
        "{\"date\":\"2024-05-10\",\"amountMinor\":"
            + amount
            + ",\"type\":\"expense\",\"accountId\":"
            + account
            + cat
            + "}");
  }

  private void expenseForeign(
      RegisteredUser user, long account, long amount, String currency, String rate, long categoryId)
      throws Exception {
    tx(
        user,
        "{\"date\":\"2024-05-15\",\"amountMinor\":"
            + amount
            + ",\"type\":\"expense\",\"accountId\":"
            + account
            + ",\"categoryId\":"
            + categoryId
            + ",\"currency\":\""
            + currency
            + "\",\"rateToBase\":"
            + rate
            + "}");
  }

  private void income(RegisteredUser user, long account, long amount) throws Exception {
    tx(
        user,
        "{\"date\":\"2024-05-20\",\"amountMinor\":"
            + amount
            + ",\"type\":\"income\",\"accountId\":"
            + account
            + "}");
  }

  private void incomeCategorized(RegisteredUser user, long account, long amount, long categoryId)
      throws Exception {
    tx(
        user,
        "{\"date\":\"2024-05-20\",\"amountMinor\":"
            + amount
            + ",\"type\":\"income\",\"accountId\":"
            + account
            + ",\"categoryId\":"
            + categoryId
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
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("id").asLong();
  }
}
