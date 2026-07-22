package com.financetracker.fx;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cross-user isolation and validation for the FX rate table. Rates are per user: one user's
 * exchange rates must never appear in — or be writable from — another user's session.
 */
class FxRateIsolationTest extends AbstractIntegrationTest {

  @Test
  void ratesAreScopedToTheOwningUser() throws Exception {
    RegisteredUser alice = register("fx-alice@example.com", "password123");
    RegisteredUser bob = register("fx-bob@example.com", "password123");

    putRate(alice, "EUR", "4.30").andExpect(status().isOk());

    listRates(alice)
        .andExpect(jsonPath("$.baseCurrency").value("PLN"))
        .andExpect(jsonPath("$.rates.length()").value(1))
        .andExpect(jsonPath("$.rates[0].currency").value("EUR"))
        .andExpect(jsonPath("$.rates[0].stale").value(false));

    // Bob's table is untouched by Alice's write.
    listRates(bob).andExpect(jsonPath("$.rates.length()").value(0));

    // Bob writing the same currency creates his own row, not a shared one.
    putRate(bob, "EUR", "4.99").andExpect(status().isOk());
    listRates(alice).andExpect(jsonPath("$.rates[0].rateToBase").value(4.30));
    listRates(bob).andExpect(jsonPath("$.rates[0].rateToBase").value(4.99));

    // Deleting Bob's rate leaves Alice's in place.
    mockMvc
        .perform(delete("/api/v1/fx/rates/EUR").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNoContent());
    listRates(bob).andExpect(jsonPath("$.rates.length()").value(0));
    listRates(alice).andExpect(jsonPath("$.rates.length()").value(1));
  }

  @Test
  void upsertReplacesRatherThanDuplicating() throws Exception {
    RegisteredUser user = register("fx-upsert@example.com", "password123");

    putRate(user, "usd", "3.90").andExpect(status().isOk());
    // Lower-case in the path is normalized, and the second write updates the same row.
    putRate(user, "USD", "4.05").andExpect(status().isOk());

    listRates(user)
        .andExpect(jsonPath("$.rates.length()").value(1))
        .andExpect(jsonPath("$.rates[0].currency").value("USD"))
        .andExpect(jsonPath("$.rates[0].rateToBase").value(4.05));
  }

  @Test
  void baseCurrencyRateCannotBeSet() throws Exception {
    RegisteredUser user = register("fx-base@example.com", "password123");
    // PLN is the reporting currency; its rate to itself is 1 by definition, never stored.
    putRate(user, "PLN", "1.00").andExpect(status().isUnprocessableEntity());
  }

  @Test
  void invalidRatesAndCurrenciesAreRejected() throws Exception {
    RegisteredUser user = register("fx-invalid@example.com", "password123");

    putRate(user, "EUR", "0").andExpect(status().isUnprocessableEntity());
    putRate(user, "EUR", "-1.5").andExpect(status().isUnprocessableEntity());
    putRate(user, "EURO", "4.30").andExpect(status().isUnprocessableEntity());
  }

  @Test
  void deletingAnAbsentRateIs404() throws Exception {
    RegisteredUser user = register("fx-missing@example.com", "password123");
    mockMvc
        .perform(delete("/api/v1/fx/rates/GBP").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNotFound());
  }

  @Test
  void ratesRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/fx/rates")).andExpect(status().isUnauthorized());
  }

  private ResultActions putRate(RegisteredUser user, String currency, String rate)
      throws Exception {
    return mockMvc.perform(
        put("/api/v1/fx/rates/" + currency)
            .header(HttpHeaders.AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"rateToBase\":" + rate + "}"));
  }

  private ResultActions listRates(RegisteredUser user) throws Exception {
    return mockMvc
        .perform(get("/api/v1/fx/rates").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk());
  }
}
