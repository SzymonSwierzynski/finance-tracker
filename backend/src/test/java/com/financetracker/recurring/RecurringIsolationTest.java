package com.financetracker.recurring;

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
 * Recurring templates: CRUD, ownership/isolation, category/type validation, and materialization (a
 * fixed past date range makes the count deterministic regardless of "today").
 */
class RecurringIsolationTest extends AbstractIntegrationTest {

  @Test
  void createListUpdateDelete() throws Exception {
    RegisteredUser user = register("recur-crud@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long rentCat = createCategory(user, "Rent", "expense");

    long id =
        createRecurring(
            user,
            "{\"accountId\":"
                + account
                + ",\"amountMinor\":150000,\"type\":\"expense\",\"categoryId\":"
                + rentCat
                + ",\"description\":\"Rent\",\"frequency\":\"monthly\",\"startDate\":\"2026-01-01\"}");

    mockMvc
        .perform(get("/api/v1/recurring").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].description").value("Rent"))
        .andExpect(jsonPath("$[0].frequency").value("monthly"))
        .andExpect(jsonPath("$[0].nextRunDate").value("2026-01-01"));

    mockMvc
        .perform(
            patch("/api/v1/recurring/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"amountMinor\":160000,\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amountMinor").value(160000))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.version").value(1));

    mockMvc
        .perform(delete("/api/v1/recurring/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/recurring").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void materializesDueOccurrencesAndDeactivatesWhenComplete() throws Exception {
    RegisteredUser user = register("recur-run@example.com", "password123");
    long account = createAccount(user);
    // A fixed, fully-past monthly range -> exactly three occurrences regardless of the current
    // date.
    createRecurring(
        user,
        "{\"accountId\":"
            + account
            + ",\"amountMinor\":5000000,\"type\":\"income\",\"description\":\"Salary\","
            + "\"frequency\":\"monthly\",\"startDate\":\"2020-01-01\",\"endDate\":\"2020-03-01\"}");

    mockMvc
        .perform(post("/api/v1/recurring/run").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.materialized").value(3)); // Jan, Feb, Mar 2020

    mockMvc
        .perform(
            get("/api/v1/transactions?accountId=" + account)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3));

    // Template is spent: advanced past endDate and deactivated; a second run is a no-op.
    mockMvc
        .perform(get("/api/v1/recurring").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].active").value(false))
        .andExpect(jsonPath("$[0].nextRunDate").value("2020-04-01"));
    mockMvc
        .perform(post("/api/v1/recurring/run").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.materialized").value(0));
  }

  @Test
  void rejectsACategoryOfTheWrongKind() throws Exception {
    RegisteredUser user = register("recur-kind@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long salary = createCategory(user, "Salary", "income");

    mockMvc
        .perform(
            post("/api/v1/recurring")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"accountId\":"
                        + account
                        + ",\"amountMinor\":1000,\"type\":\"expense\",\"categoryId\":"
                        + salary
                        + ",\"frequency\":\"monthly\",\"startDate\":\"2026-01-01\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void usersCannotReachEachOthersRecurring() throws Exception {
    RegisteredUser alice = register("recur-a@example.com", "password123");
    RegisteredUser bob = register("recur-b@example.com", "password123");
    long aliceAccount = createAccount(alice);
    long aliceRecurring =
        createRecurring(
            alice,
            "{\"accountId\":"
                + aliceAccount
                + ",\"amountMinor\":1000,\"type\":\"expense\",\"frequency\":\"monthly\","
                + "\"startDate\":\"2026-01-01\"}");

    mockMvc
        .perform(get("/api/v1/recurring").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            patch("/api/v1/recurring/" + aliceRecurring)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"active\":false}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/api/v1/recurring/" + aliceRecurring)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/recurring")).andExpect(status().isUnauthorized());
  }

  private long createRecurring(RegisteredUser user, String json) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/recurring")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
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

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
