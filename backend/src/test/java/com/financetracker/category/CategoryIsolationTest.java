package com.financetracker.category;

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
 * Category CRUD, the two-level rule, kind/uniqueness constraints, delete-uncategorizes, isolation.
 */
class CategoryIsolationTest extends AbstractIntegrationTest {

  @Test
  void createSubcategoryAndList() throws Exception {
    RegisteredUser user = register("cat-crud@example.com", "password123");
    clearCategories(user);
    long food = createCategory(user, "{\"name\":\"Food\",\"kind\":\"expense\"}");
    createCategory(user, "{\"name\":\"Groceries\",\"kind\":\"expense\",\"parentId\":" + food + "}");

    mockMvc
        .perform(
            get("/api/v1/categories?kind=expense").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void enforcesTwoLevelsOnly() throws Exception {
    RegisteredUser user = register("cat-twolevel@example.com", "password123");
    long top = createCategory(user, "{\"name\":\"Top\",\"kind\":\"expense\"}");
    long sub =
        createCategory(user, "{\"name\":\"Sub\",\"kind\":\"expense\",\"parentId\":" + top + "}");

    // A category whose parent already has a parent is rejected.
    mockMvc
        .perform(
            post("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Deep\",\"kind\":\"expense\",\"parentId\":" + sub + "}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void subcategoryKindMustMatchParent() throws Exception {
    RegisteredUser user = register("cat-kind@example.com", "password123");
    clearCategories(user);
    long salary = createCategory(user, "{\"name\":\"Salary\",\"kind\":\"income\"}");
    mockMvc
        .perform(
            post("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bonus\",\"kind\":\"expense\",\"parentId\":" + salary + "}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void duplicateTopLevelNameConflicts() throws Exception {
    RegisteredUser user = register("cat-dupe@example.com", "password123");
    createCategory(user, "{\"name\":\"Food\",\"kind\":\"expense\"}");
    mockMvc
        .perform(
            post("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"food\",\"kind\":\"expense\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void deletingCategoryUncategorizesItsTransactions() throws Exception {
    RegisteredUser user = register("cat-delete@example.com", "password123");
    long account = createAccount(user);
    long food = createCategory(user, "{\"name\":\"Food\",\"kind\":\"expense\"}");
    long txId =
        createTx(
            user,
            "{\"date\":\"2024-06-01\",\"amountMinor\":1000,\"type\":\"expense\",\"accountId\":"
                + account
                + ",\"categoryId\":"
                + food
                + "}");

    mockMvc
        .perform(
            delete("/api/v1/categories/" + food).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uncategorizedTransactions").value(1));

    // The transaction survives but is now uncategorized.
    mockMvc
        .perform(
            get("/api/v1/transactions/" + txId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categoryId").doesNotExist());
  }

  @Test
  void categorizationRejectsKindMismatch() throws Exception {
    RegisteredUser user = register("cat-mismatch@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long incomeCat = createCategory(user, "{\"name\":\"Salary\",\"kind\":\"income\"}");
    // An expense transaction cannot take an income category.
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2024-06-01\",\"amountMinor\":1000,\"type\":\"expense\",\"accountId\":"
                        + account
                        + ",\"categoryId\":"
                        + incomeCat
                        + "}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void usersCannotReachEachOthersCategories() throws Exception {
    RegisteredUser alice = register("cat-alice@example.com", "password123");
    RegisteredUser bob = register("cat-bob@example.com", "password123");
    clearCategories(bob);
    long aliceCat = createCategory(alice, "{\"name\":\"Food\",\"kind\":\"expense\"}");

    mockMvc
        .perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            patch("/api/v1/categories/" + aliceCat)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"name\":\"Hacked\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/api/v1/categories/" + aliceCat).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
  }

  private long createCategory(RegisteredUser user, String json) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return id(result);
  }

  private long createAccount(RegisteredUser user) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Checking\",\"type\":\"checking\",\"currency\":\"PLN\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return id(result);
  }

  private long createTx(RegisteredUser user, String json) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/transactions")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return id(result);
  }

  private long id(MvcResult result) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("id").asLong();
  }
}
