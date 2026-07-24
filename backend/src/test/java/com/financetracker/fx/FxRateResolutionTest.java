package com.financetracker.fx;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * How a rate gets onto a transaction, and — the part that actually matters — what happens to it
 * afterwards. Rates are looked up once, at entry, and frozen: no later edit to the rate table and
 * no change of reporting currency may move a stored transaction's base value (CLAUDE.md §7).
 */
class FxRateResolutionTest extends AbstractIntegrationTest {

  @Test
  void foreignCurrencyNeedsARateAndThenLocksIt() throws Exception {
    RegisteredUser user = register("fx-resolve@example.com", "password123");
    long account = createAccount(user, "Euro cash", "EUR");

    // No rate on file and none supplied: refuse rather than guess. A guess here would be frozen
    // onto the transaction forever.
    createTransaction(user, account, 10_000L, null).andExpect(status().isUnprocessableEntity());

    putRate(user, "EUR", "4.30").andExpect(status().isOk());

    // 100.00 EUR at 4.30 -> 430.00 PLN. Asserted to the grosz.
    createTransaction(user, account, 10_000L, null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amountMinor").value(10_000))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.rateToBase").value(4.30))
        .andExpect(jsonPath("$.baseMinor").value(43_000));
  }

  @Test
  void anExplicitRateBeatsTheTable() throws Exception {
    RegisteredUser user = register("fx-explicit@example.com", "password123");
    long account = createAccount(user, "Euro cash", "EUR");
    putRate(user, "EUR", "4.30").andExpect(status().isOk());

    // The rate the bank actually applied wins over the user's standing table entry.
    createTransaction(user, account, 10_000L, "4.51")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateToBase").value(4.51))
        .andExpect(jsonPath("$.baseMinor").value(45_100));
  }

  @Test
  void changingReportingCurrencyStalesTheTableButNeverRewritesHistory() throws Exception {
    RegisteredUser user = register("fx-rebase@example.com", "password123");
    long account = createAccount(user, "Euro cash", "EUR");
    putRate(user, "EUR", "4.30").andExpect(status().isOk());

    MvcResult created =
        createTransaction(user, account, 10_000L, null).andExpect(status().isCreated()).andReturn();
    long transactionId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    // Switch the base currency. 4.30 was PLN-per-EUR; it is not USD-per-EUR.
    mockMvc
        .perform(
            put("/api/v1/settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportingCurrency\":\"USD\"}"))
        .andExpect(status().isOk());

    // The stored rate is kept but flagged: it is no longer anchored to the reporting currency.
    mockMvc
        .perform(get("/api/v1/fx/rates").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseCurrency").value("USD"))
        .andExpect(jsonPath("$.rates[0].baseCurrency").value("PLN"))
        .andExpect(jsonPath("$.rates[0].stale").value(true));

    // A stale rate is treated as no rate: refuse the new transaction rather than book 4.30 USD/EUR.
    createTransaction(user, account, 10_000L, null).andExpect(status().isUnprocessableEntity());

    // The existing transaction is untouched — same locked rate, same base value, to the grosz.
    mockMvc
        .perform(
            get("/api/v1/transactions/" + transactionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rateToBase").value(4.30))
        .andExpect(jsonPath("$.baseMinor").value(43_000));
  }

  @Test
  void reportingCurrencyResolvesToOneWithoutATableEntry() throws Exception {
    RegisteredUser user = register("fx-base-ccy@example.com", "password123");
    long account = createAccount(user, "Cash", "PLN");

    createTransaction(user, account, 1_999L, null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateToBase").value(1))
        .andExpect(jsonPath("$.baseMinor").value(1_999));
  }

  private long createAccount(RegisteredUser user, String name, String currency) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\""
                            + name
                            + "\",\"type\":\"cash\",\"currency\":\""
                            + currency
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("id").asLong();
  }

  private ResultActions createTransaction(
      RegisteredUser user, long accountId, long amountMinor, String rateToBase) throws Exception {
    String rateField = rateToBase == null ? "" : ",\"rateToBase\":" + rateToBase;
    return mockMvc.perform(
        post("/api/v1/transactions")
            .header(HttpHeaders.AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"date\":\"2026-07-01\",\"amountMinor\":"
                    + amountMinor
                    + ",\"type\":\"expense\",\"accountId\":"
                    + accountId
                    + rateField
                    + "}"));
  }

  private ResultActions putRate(RegisteredUser user, String currency, String rate)
      throws Exception {
    return mockMvc.perform(
        put("/api/v1/fx/rates/" + currency)
            .header(HttpHeaders.AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"rateToBase\":" + rate + "}"));
  }
}
