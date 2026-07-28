package com.financetracker.common.idempotency;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Idempotency-Key on POST /transactions and POST /imports/commit: replay vs duplicate, strict
 * fingerprint mismatch, per-user isolation, and retention purge. Shared-DB pitfall (§13): unique
 * users, per-user assertions.
 */
class IdempotencyIsolationTest extends AbstractIntegrationTest {

  @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
  @Autowired private IdempotencyKeyCleanup idempotencyKeyCleanup;
  @Autowired private PlatformTransactionManager transactionManager;

  private static final String TX =
      "{\"date\":\"2026-05-01\",\"amountMinor\":1999,\"type\":\"expense\",\"accountId\":%d}";

  @Test
  void sameKeyReplaysTheTransactionInsteadOfDuplicating() throws Exception {
    RegisteredUser user = register("idem-tx@example.com", "password123");
    long account = createAccount(user);
    String body = String.format(TX, account);

    MvcResult first =
        mockMvc.perform(postTx(user, body, "key-1")).andExpect(status().isCreated()).andReturn();
    long firstId = id(first);

    // Same key + same body -> replay: identical id, no second row.
    mockMvc
        .perform(postTx(user, body, "key-1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(firstId));
    assertThat(transactionCount(user)).isEqualTo(1);
  }

  @Test
  void sameKeyDifferentBodyIsRejected() throws Exception {
    RegisteredUser user = register("idem-mismatch@example.com", "password123");
    long account = createAccount(user);
    mockMvc
        .perform(postTx(user, String.format(TX, account), "key-2"))
        .andExpect(status().isCreated());

    String different =
        "{\"date\":\"2026-05-01\",\"amountMinor\":9999,\"type\":\"expense\",\"accountId\":"
            + account
            + "}";
    mockMvc.perform(postTx(user, different, "key-2")).andExpect(status().isUnprocessableEntity());
  }

  @Test
  void withoutKeyDuplicatesAsBefore() throws Exception {
    RegisteredUser user = register("idem-nokey@example.com", "password123");
    long account = createAccount(user);
    String body = String.format(TX, account);
    mockMvc.perform(postTx(user, body, null)).andExpect(status().isCreated());
    mockMvc.perform(postTx(user, body, null)).andExpect(status().isCreated());
    assertThat(transactionCount(user)).isEqualTo(2);
  }

  @Test
  void keysAreScopedPerUser() throws Exception {
    RegisteredUser alice = register("idem-a@example.com", "password123");
    RegisteredUser bob = register("idem-b@example.com", "password123");
    long aliceAcct = createAccount(alice);
    long bobAcct = createAccount(bob);
    // Same key string for both users is independent — Bob is not blocked or replayed by Alice's
    // key.
    mockMvc
        .perform(postTx(alice, String.format(TX, aliceAcct), "shared"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(postTx(bob, String.format(TX, bobAcct), "shared"))
        .andExpect(status().isCreated());
    assertThat(transactionCount(alice)).isEqualTo(1);
    assertThat(transactionCount(bob)).isEqualTo(1);
  }

  @Test
  void importCommitReplaysInsteadOfReExecuting() throws Exception {
    RegisteredUser user = register("idem-import@example.com", "password123");
    long account = createAccount(user);

    mockMvc
        .perform(commit(user, account, "key-imp"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(2));
    // Replay: same body echoed (imported=2), NOT the dedupe path (which would report imported=0).
    mockMvc
        .perform(commit(user, account, "key-imp"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(2));
    // One batch, two transactions — the second commit created nothing.
    mockMvc
        .perform(get("/api/v1/imports/batches").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    assertThat(transactionCount(user)).isEqualTo(2);
  }

  @Test
  void purgedKeysNoLongerReplay() throws Exception {
    RegisteredUser user = register("idem-purge@example.com", "password123");
    long account = createAccount(user);
    String body = String.format(TX, account);
    long firstId = id(mockMvc.perform(postTx(user, body, "key-purge")).andReturn());

    // Retention purge (a cutoff in the future removes everything): the modifying query needs a
    // transaction, so run it through a TransactionTemplate like the @Transactional cleanup does.
    int deleted =
        new TransactionTemplate(transactionManager)
            .execute(
                s -> idempotencyKeyRepository.deleteCreatedBefore(Instant.now().plusSeconds(5)));
    assertThat(deleted).isGreaterThanOrEqualTo(1);

    // The same key now creates a fresh transaction instead of replaying.
    long secondId = id(mockMvc.perform(postTx(user, body, "key-purge")).andReturn());
    assertThat(secondId).isNotEqualTo(firstId);
  }

  @Test
  void cleanupComponentRunsWithinItsOwnTransaction() {
    // Covers the nightly @Scheduled purge; on fresh data it deletes nothing and must not throw.
    idempotencyKeyCleanup.purgeOld();
  }

  // --- helpers ---

  private MockHttpServletRequestBuilder postTx(RegisteredUser user, String body, String key) {
    MockHttpServletRequestBuilder b =
        post("/api/v1/transactions")
            .header(HttpHeaders.AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    return key == null ? b : b.header("Idempotency-Key", key);
  }

  private MockHttpServletRequestBuilder commit(RegisteredUser user, long account, String key) {
    String csv = "Date;Title;Amount\n15.05.2026;Shop;-19,99\n16.05.2026;Pay;5 000,00\n";
    String mapping =
        "{\"delimiter\":\";\",\"encoding\":\"utf-8\",\"hasHeader\":true,\"dateIndex\":0,"
            + "\"dateFormat\":\"auto\",\"descriptionIndex\":1,\"amountMode\":\"signed\","
            + "\"amountIndex\":2,\"expenseIsNegative\":true,\"debitIndex\":-1,\"creditIndex\":-1}";
    return multipart("/api/v1/imports/commit")
        .file(new MockMultipartFile("file", "may.csv", "text/csv", csv.getBytes(UTF_8)))
        .file(new MockMultipartFile("mapping", "", "application/json", mapping.getBytes(UTF_8)))
        .param("accountId", String.valueOf(account))
        .header(HttpHeaders.AUTHORIZATION, bearer(user))
        .header("Idempotency-Key", key);
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

  private int transactionCount(RegisteredUser user) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/transactions?size=200")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("items").size();
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
