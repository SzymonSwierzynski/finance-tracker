package com.financetracker.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Auto-categorization rules: CRUD, ownership/isolation, category-reference validation, and applying
 * rules over uncategorized transactions (respecting the category/type kind invariant).
 */
class RuleIsolationTest extends AbstractIntegrationTest {

  @Test
  void createListUpdateDelete() throws Exception {
    RegisteredUser user = register("rule-crud@example.com", "password123");
    clearCategories(user);
    long food = createCategory(user, "Food", "expense");

    long ruleId =
        createRule(user, "{\"pattern\":\"lidl\",\"categoryId\":" + food + ",\"priority\":5}");

    mockMvc
        .perform(get("/api/v1/rules").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].pattern").value("lidl"))
        .andExpect(jsonPath("$[0].priority").value(5));

    mockMvc
        .perform(
            patch("/api/v1/rules/" + ruleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"pattern\":\"biedronka\",\"priority\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pattern").value("biedronka"))
        .andExpect(jsonPath("$.priority").value(3))
        .andExpect(jsonPath("$.version").value(1));

    mockMvc
        .perform(delete("/api/v1/rules/" + ruleId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/rules").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void rejectsARuleForAnotherUsersCategory() throws Exception {
    RegisteredUser alice = register("rule-owner-a@example.com", "password123");
    RegisteredUser bob = register("rule-owner-b@example.com", "password123");
    clearCategories(alice);
    long aliceFood = createCategory(alice, "Food", "expense");

    mockMvc
        .perform(
            post("/api/v1/rules")
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"x\",\"categoryId\":" + aliceFood + "}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void usersCannotReachEachOthersRules() throws Exception {
    RegisteredUser alice = register("rule-iso-a@example.com", "password123");
    RegisteredUser bob = register("rule-iso-b@example.com", "password123");
    clearCategories(alice);
    long aliceFood = createCategory(alice, "Food", "expense");
    long aliceRule = createRule(alice, "{\"pattern\":\"lidl\",\"categoryId\":" + aliceFood + "}");

    mockMvc
        .perform(get("/api/v1/rules").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            patch("/api/v1/rules/" + aliceRule)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"priority\":9}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/api/v1/rules/" + aliceRule).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
  }

  @Test
  void applyCategorizesUncategorizedTransactions() throws Exception {
    RegisteredUser user = register("rule-apply@example.com", "password123");
    clearCategories(user);
    long groceries = createCategory(user, "Groceries", "expense");
    long account = createAccount(user);
    long matching = createExpense(user, account, 1999, "Payment BIEDRONKA 4012");
    long other = createExpense(user, account, 3000, "Unknown vendor");
    createRule(user, "{\"pattern\":\"biedronka\",\"categoryId\":" + groceries + ",\"priority\":1}");

    mockMvc
        .perform(post("/api/v1/rules/apply").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scanned").value(2))
        .andExpect(jsonPath("$.categorized").value(1));

    assertThat(txCategoryId(user, matching)).isEqualTo(groceries);
    assertThat(txCategoryId(user, other)).isNull();
  }

  @Test
  void applyRespectsTheCategoryKind() throws Exception {
    RegisteredUser user = register("rule-kind@example.com", "password123");
    clearCategories(user);
    long salary = createCategory(user, "Salary", "income");
    long account = createAccount(user);
    // An expense whose description matches a rule that points at an income category: must be
    // skipped.
    long expense = createExpense(user, account, 500000, "pensja od pracodawcy");
    createRule(user, "{\"pattern\":\"pensja\",\"categoryId\":" + salary + ",\"priority\":1}");

    mockMvc
        .perform(post("/api/v1/rules/apply").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categorized").value(0));

    assertThat(txCategoryId(user, expense)).isNull();
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/rules")).andExpect(status().isUnauthorized());
  }

  // --- helpers ---

  private long createRule(RegisteredUser user, String json) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/rules")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
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

  private long createExpense(
      RegisteredUser user, long account, long amountMinor, String description) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/transactions")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"date\":\"2026-05-15\",\"amountMinor\":"
                            + amountMinor
                            + ",\"type\":\"expense\",\"accountId\":"
                            + account
                            + ",\"description\":\""
                            + description
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private Long txCategoryId(RegisteredUser user, long txId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/transactions/" + txId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode categoryId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("categoryId");
    return categoryId == null || categoryId.isNull() ? null : categoryId.asLong();
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
