package com.financetracker.importing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Phase 4 acceptance — ported from the prototype's {@code data/import.integration.test.ts}: a real
 * Polish CSV (';' delimiter, decimal comma, space thousands) previews, imports, auto-categorizes
 * via a rule, fully deduplicates on re-import, remembers its mapping, and undoes.
 */
class ImportPipelineTest extends AbstractIntegrationTest {

  private static final String CSV =
      """
      Date;Title;Amount
      15.05.2026;Płatność BIEDRONKA 4012;-19,99
      16.05.2026;Pensja;5 000,00
      """;

  /**
   * Two byte-identical rows. A bank export never lists one transaction twice, so this is two real
   * payments on the same day for the same amount at the same merchant — exactly the shape that the
   * old set-based dedupe silently discarded (a real 3-month mBank export lost 4 rows this way).
   */
  private static final String CSV_WITH_GENUINE_PAIR =
      """
      Date;Title;Amount
      05.06.2026;NEW VEGAS;-36,00
      05.06.2026;NEW VEGAS;-36,00
      """;

  private static final String MAPPING =
      "{\"delimiter\":\";\",\"encoding\":\"utf-8\",\"hasHeader\":true,\"dateIndex\":0,"
          + "\"dateFormat\":\"auto\",\"descriptionIndex\":1,\"amountMode\":\"signed\","
          + "\"amountIndex\":2,\"expenseIsNegative\":true,\"debitIndex\":-1,\"creditIndex\":-1}";

  @Test
  void previewsImportsAutoCategorizesDedupesAndUndoes() throws Exception {
    RegisteredUser user = register("import-flow@example.com", "password123");
    clearCategories(user);
    long groceries = createCategory(user, "Groceries", "expense");
    long account = createAccount(user);
    createRule(user, "{\"pattern\":\"biedronka\",\"categoryId\":" + groceries + ",\"priority\":1}");

    // Preview: two valid, non-duplicate rows; sign convention splits expense vs income.
    mockMvc
        .perform(multipartImport("/api/v1/imports/preview", user, account))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(2))
        .andExpect(jsonPath("$.validRows").value(2))
        .andExpect(jsonPath("$.duplicateRows").value(0))
        .andExpect(jsonPath("$.rows[0].type").value("expense"))
        .andExpect(jsonPath("$.rows[0].amountMinor").value(1999))
        .andExpect(jsonPath("$.rows[1].type").value("income"))
        .andExpect(jsonPath("$.rows[1].amountMinor").value(500000));

    // Commit: both imported.
    MvcResult committed =
        mockMvc
            .perform(multipartImport("/api/v1/imports/commit", user, account))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.imported").value(2))
            .andExpect(jsonPath("$.skippedDuplicates").value(0))
            .andReturn();
    long batchId =
        objectMapper.readTree(committed.getResponse().getContentAsString()).get("batchId").asLong();

    JsonNode items = transactions(user, account);
    assertThat(items).hasSize(2);
    JsonNode biedronka = findByDescriptionContaining(items, "BIEDRONKA");
    assertThat(biedronka.get("type").asText()).isEqualTo("expense");
    assertThat(biedronka.get("amountMinor").asLong()).isEqualTo(1999);
    assertThat(biedronka.get("categoryId").asLong()).isEqualTo(groceries); // rule applied
    JsonNode pensja = findByDescriptionContaining(items, "Pensja");
    assertThat(pensja.get("type").asText()).isEqualTo("income");
    assertThat(pensja.get("amountMinor").asLong()).isEqualTo(500000);
    assertThat(pensja.hasNonNull("categoryId")).isFalse(); // no matching rule -> uncategorized

    // Re-importing the same file is fully deduplicated.
    mockMvc
        .perform(multipartImport("/api/v1/imports/commit", user, account))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(0))
        .andExpect(jsonPath("$.skippedDuplicates").value(2));

    // Only the first (non-empty) import produced a batch, and the mapping is remembered.
    mockMvc
        .perform(get("/api/v1/imports/batches").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mockMvc
        .perform(
            get("/api/v1/imports/profiles/" + account)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amountMode").value("signed"));

    // Undo removes the batch's transactions.
    mockMvc
        .perform(
            delete("/api/v1/imports/batches/" + batchId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());
    assertThat(transactions(user, account)).isEmpty();
    mockMvc
        .perform(get("/api/v1/imports/batches").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void importsEveryOccurrenceOfARepeatedTransactionButStaysIdempotent() throws Exception {
    RegisteredUser user = register("dupe-pair@example.com", "password123");
    long account = createAccount(user);

    // Both rows are real payments, so both must land.
    mockMvc
        .perform(multipartImport("/api/v1/imports/commit", user, account, CSV_WITH_GENUINE_PAIR))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(2))
        .andExpect(jsonPath("$.skippedDuplicates").value(0));

    // Re-uploading the same file adds nothing: the account now holds as many as the file claims.
    mockMvc
        .perform(multipartImport("/api/v1/imports/commit", user, account, CSV_WITH_GENUINE_PAIR))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(0))
        .andExpect(jsonPath("$.skippedDuplicates").value(2));

    assertThat(transactions(user, account)).hasSize(2);
  }

  // --- helpers ---

  private MockHttpServletRequestBuilder multipartImport(
      String path, RegisteredUser user, long account) {
    return multipartImport(path, user, account, CSV);
  }

  private MockHttpServletRequestBuilder multipartImport(
      String path, RegisteredUser user, long account, String csv) {
    MockMultipartFile file =
        new MockMultipartFile("file", "may.csv", "text/csv", csv.getBytes(UTF_8));
    MockMultipartFile mapping =
        new MockMultipartFile("mapping", "", "application/json", MAPPING.getBytes(UTF_8));
    return multipart(path)
        .file(file)
        .file(mapping)
        .param("accountId", String.valueOf(account))
        .header(HttpHeaders.AUTHORIZATION, bearer(user));
  }

  private JsonNode transactions(RegisteredUser user, long account) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/transactions?size=50&accountId=" + account)
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
  }

  private static JsonNode findByDescriptionContaining(JsonNode items, String needle) {
    for (JsonNode item : items) {
      if (item.get("description").asText().contains(needle)) {
        return item;
      }
    }
    throw new AssertionError("No transaction description contains: " + needle);
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

  private void createRule(RegisteredUser user, String json) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rules")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
