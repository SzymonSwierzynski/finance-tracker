package com.financetracker.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Cross-user isolation: a user can only ever read/write their own settings, and one user's update
 * never bleeds into another's. The endpoint takes no user id — scoping comes from the token.
 */
class SettingsIsolationTest extends AbstractIntegrationTest {

  @Test
  void usersOnlySeeAndChangeTheirOwnSettings() throws Exception {
    RegisteredUser alice = register("alice@example.com", "password123");
    RegisteredUser bob = register("bob@example.com", "password123");

    // Both start at the default reporting currency.
    getSettings(alice).andExpect(jsonPath("$.reportingCurrency").value("PLN"));
    getSettings(bob).andExpect(jsonPath("$.reportingCurrency").value("PLN"));

    // Alice switches to EUR.
    mockMvc
        .perform(
            put("/api/v1/settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportingCurrency\":\"eur\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportingCurrency").value("EUR")); // normalized to upper-case

    // Bob is unaffected.
    getSettings(bob).andExpect(jsonPath("$.reportingCurrency").value("PLN"));

    // Bob switches to GBP; Alice is unaffected.
    mockMvc
        .perform(
            put("/api/v1/settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportingCurrency\":\"GBP\"}"))
        .andExpect(status().isOk());

    getSettings(alice).andExpect(jsonPath("$.reportingCurrency").value("EUR"));
    getSettings(bob).andExpect(jsonPath("$.reportingCurrency").value("GBP"));
  }

  @Test
  void settingsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/settings")).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidCurrencyReturns422() throws Exception {
    RegisteredUser user = register("badccy@example.com", "password123");
    mockMvc
        .perform(
            put("/api/v1/settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportingCurrency\":\"EURO\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  private org.springframework.test.web.servlet.ResultActions getSettings(RegisteredUser user)
      throws Exception {
    return mockMvc
        .perform(get("/api/v1/settings").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk());
  }
}
