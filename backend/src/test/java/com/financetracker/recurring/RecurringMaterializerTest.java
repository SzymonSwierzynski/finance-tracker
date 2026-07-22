package com.financetracker.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.financetracker.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The scheduled nightly sweep ({@link RecurringMaterializer} → {@code materializeAllDue}) advances
 * every user's due templates in one pass, with no request-scoped user. The correctness point: each
 * transaction locks the rate from its <em>own owner's</em> FX table, not some single caller's — so
 * a cross-user run stays correct (CLAUDE.md §7). Two users hold the same currency at different
 * rates to prove exactly that.
 */
class RecurringMaterializerTest extends AbstractIntegrationTest {

  @Autowired private RecurringTransactionService recurringService;

  @Test
  void sweepMaterializesEveryUsersDueTemplatesEachWithItsOwnLockedRate() throws Exception {
    RegisteredUser alice = register("sweep-a@example.com", "password123");
    RegisteredUser bob = register("sweep-b@example.com", "password123");
    setEurRate(alice, "4");
    setEurRate(bob, "5");
    long aliceAccount = createEurAccount(alice);
    long bobAccount = createEurAccount(bob);
    // A fixed, fully-past monthly range → exactly two occurrences (Jan, Feb 2020) per template,
    // regardless of the current date.
    createEurRecurring(alice, aliceAccount, 10000);
    createEurRecurring(bob, bobAccount, 20000);

    int created = recurringService.materializeAllDue();

    // The sweep is global and the Testcontainers Postgres is shared across the whole suite (no
    // rollback), so sibling tests' active templates are swept too — hence assert on our own users'
    // outcomes, not an exact global count. Our two fresh templates always add four occurrences.
    assertThat(created).isGreaterThanOrEqualTo(4);
    // Each user's transactions carry that user's own EUR rate — proving per-owner rate resolution.
    assertRowsAllHave(alice, 10000, "4");
    assertRowsAllHave(bob, 20000, "5");
  }

  /**
   * Every exported row for the user carries the given native amount and that user's own EUR rate.
   */
  private void assertRowsAllHave(RegisteredUser user, long amountMinor, String rate)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/export/transactions").header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(rows).hasSize(2);
    for (JsonNode row : rows) {
      assertThat(row.get("currency").asText()).isEqualTo("EUR");
      assertThat(row.get("amountMinor").asLong()).isEqualTo(amountMinor);
      assertThat(new BigDecimal(row.get("rateToBase").asText())).isEqualByComparingTo(rate);
    }
  }

  private void setEurRate(RegisteredUser user, String rate) throws Exception {
    mockMvc
        .perform(
            put("/api/v1/fx/rates/EUR")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rateToBase\":" + rate + "}"))
        .andExpect(status().isOk());
  }

  private long createEurAccount(RegisteredUser user) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"EUR\",\"type\":\"checking\",\"currency\":\"EUR\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long createEurRecurring(RegisteredUser user, long account, long amountMinor)
      throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/recurring")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"accountId\":"
                            + account
                            + ",\"amountMinor\":"
                            + amountMinor
                            + ",\"type\":\"expense\",\"frequency\":\"monthly\","
                            + "\"startDate\":\"2020-01-01\",\"endDate\":\"2020-02-01\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
