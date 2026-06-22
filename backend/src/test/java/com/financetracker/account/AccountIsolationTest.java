package com.financetracker.account;

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

/** Accounts CRUD, validation, optimistic locking, balance, and cross-user isolation. */
class AccountIsolationTest extends AbstractIntegrationTest {

  @Test
  void createListArchiveAndFetch() throws Exception {
    RegisteredUser user = register("acc-crud@example.com", "password123");

    long id =
        createAccount(
            user, "{\"name\":\"  Checking  \",\"type\":\"checking\",\"currency\":\"pln\"}");

    // Name trimmed, currency upper-cased, sensible defaults.
    mockMvc
        .perform(get("/api/v1/accounts/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Checking"))
        .andExpect(jsonPath("$.currency").value("PLN"))
        .andExpect(jsonPath("$.trackBalance").value(false))
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.startingBalanceMinor").doesNotExist());

    // Listed by default.
    mockMvc
        .perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    // Archive -> hidden by default, visible with includeArchived.
    mockMvc
        .perform(
            post("/api/v1/accounts/" + id + "/archive")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            get("/api/v1/accounts?includeArchived=true")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].archived").value(true));
  }

  @Test
  void startingBalanceOnlyKeptWhenTracking() throws Exception {
    RegisteredUser user = register("acc-startbal@example.com", "password123");

    // trackBalance false -> startingBalanceMinor dropped.
    long noTrack =
        createAccount(
            user,
            "{\"name\":\"Cash\",\"type\":\"cash\",\"currency\":\"PLN\",\"startingBalanceMinor\":5000}");
    mockMvc
        .perform(get("/api/v1/accounts/" + noTrack).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(jsonPath("$.startingBalanceMinor").doesNotExist());

    // trackBalance true -> kept.
    long track =
        createAccount(
            user,
            "{\"name\":\"Savings\",\"type\":\"savings\",\"currency\":\"PLN\",\"trackBalance\":true,\"startingBalanceMinor\":5000}");
    mockMvc
        .perform(get("/api/v1/accounts/" + track).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(jsonPath("$.startingBalanceMinor").value(5000))
        .andExpect(jsonPath("$.trackBalance").value(true));
  }

  @Test
  void patchEnforcesOptimisticLocking() throws Exception {
    RegisteredUser user = register("acc-version@example.com", "password123");
    long id = createAccount(user, "{\"name\":\"A\",\"type\":\"checking\",\"currency\":\"PLN\"}");

    // Correct version (0) succeeds and bumps the version.
    mockMvc
        .perform(
            patch("/api/v1/accounts/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"name\":\"Renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"))
        .andExpect(jsonPath("$.version").value(1));

    // Stale version (0 again) -> 409.
    mockMvc
        .perform(
            patch("/api/v1/accounts/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"name\":\"Nope\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void balanceIsStartingPlusActivityAndGatedOnTracking() throws Exception {
    RegisteredUser user = register("acc-balance@example.com", "password123");

    long untracked = createAccount(user, "{\"name\":\"U\",\"type\":\"cash\",\"currency\":\"PLN\"}");
    mockMvc
        .perform(
            get("/api/v1/accounts/" + untracked + "/balance")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isUnprocessableEntity());

    long tracked =
        createAccount(
            user,
            "{\"name\":\"T\",\"type\":\"savings\",\"currency\":\"PLN\",\"trackBalance\":true,\"startingBalanceMinor\":10000}");
    addTransaction(
        user,
        "{\"date\":\"2024-03-01\",\"amountMinor\":5000,\"type\":\"income\",\"accountId\":"
            + tracked
            + "}");
    addTransaction(
        user,
        "{\"date\":\"2024-03-02\",\"amountMinor\":2000,\"type\":\"expense\",\"accountId\":"
            + tracked
            + "}");

    // 10000 + 5000 - 2000 = 13000
    mockMvc
        .perform(
            get("/api/v1/accounts/" + tracked + "/balance")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balanceMinor").value(13000))
        .andExpect(jsonPath("$.currency").value("PLN"));
  }

  @Test
  void invalidPayloadsAreRejected() throws Exception {
    RegisteredUser user = register("acc-invalid@example.com", "password123");

    // Blank name -> 422.
    mockMvc
        .perform(
            post("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"type\":\"checking\",\"currency\":\"PLN\"}"))
        .andExpect(status().isUnprocessableEntity());

    // Bad currency -> 422.
    mockMvc
        .perform(
            post("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"type\":\"checking\",\"currency\":\"PLNN\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
  }

  @Test
  void usersCannotReachEachOthersAccounts() throws Exception {
    RegisteredUser alice = register("acc-alice@example.com", "password123");
    RegisteredUser bob = register("acc-bob@example.com", "password123");

    long aliceAccount =
        createAccount(alice, "{\"name\":\"Alice\",\"type\":\"checking\",\"currency\":\"PLN\"}");

    // Bob sees an empty list and cannot read, patch or archive Alice's account (404 — no leak).
    mockMvc
        .perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            get("/api/v1/accounts/" + aliceAccount).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            patch("/api/v1/accounts/" + aliceAccount)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"name\":\"Hacked\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/accounts/" + aliceAccount + "/archive")
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());

    // Alice's account is untouched.
    mockMvc
        .perform(
            get("/api/v1/accounts/" + aliceAccount)
                .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Alice"));
  }

  private long createAccount(RegisteredUser user, String json) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("id").asLong();
  }

  private void addTransaction(RegisteredUser user, String json) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());
  }
}
