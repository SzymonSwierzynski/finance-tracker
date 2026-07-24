package com.financetracker.importing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Import endpoints are user-scoped: no user can preview/commit into, or read/undo, another's data.
 */
class ImportIsolationTest extends AbstractIntegrationTest {

  private static final String CSV = "Date;Title;Amount\n15.05.2026;Shop;-19,99\n";

  private static final String MAPPING =
      "{\"delimiter\":\";\",\"encoding\":\"utf-8\",\"hasHeader\":true,\"dateIndex\":0,"
          + "\"dateFormat\":\"auto\",\"descriptionIndex\":1,\"amountMode\":\"signed\","
          + "\"amountIndex\":2,\"expenseIsNegative\":true,\"debitIndex\":-1,\"creditIndex\":-1}";

  @Test
  void previewAndCommitRejectAnotherUsersAccount() throws Exception {
    RegisteredUser alice = register("imp-iso-a@example.com", "password123");
    RegisteredUser bob = register("imp-iso-b@example.com", "password123");
    long aliceAccount = createAccount(alice);

    mockMvc
        .perform(multipartImport("/api/v1/imports/preview", bob, aliceAccount))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(multipartImport("/api/v1/imports/commit", bob, aliceAccount))
        .andExpect(status().isNotFound());
  }

  @Test
  void usersCannotSeeOrUndoEachOthersBatches() throws Exception {
    RegisteredUser alice = register("imp-batch-a@example.com", "password123");
    RegisteredUser bob = register("imp-batch-b@example.com", "password123");
    long aliceAccount = createAccount(alice);
    MvcResult committed =
        mockMvc
            .perform(multipartImport("/api/v1/imports/commit", alice, aliceAccount))
            .andExpect(status().isCreated())
            .andReturn();
    long batchId =
        objectMapper.readTree(committed.getResponse().getContentAsString()).get("batchId").asLong();

    mockMvc
        .perform(get("/api/v1/imports/batches").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            delete("/api/v1/imports/batches/" + batchId)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
    // Alice's batch survives Bob's attempt.
    mockMvc
        .perform(get("/api/v1/imports/batches").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void profileIsScopedToTheOwner() throws Exception {
    RegisteredUser alice = register("imp-prof-a@example.com", "password123");
    RegisteredUser bob = register("imp-prof-b@example.com", "password123");
    long aliceAccount = createAccount(alice);
    mockMvc
        .perform(multipartImport("/api/v1/imports/commit", alice, aliceAccount))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/imports/profiles/" + aliceAccount)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/imports/batches")).andExpect(status().isUnauthorized());
  }

  private MockHttpServletRequestBuilder multipartImport(
      String path, RegisteredUser user, long account) {
    MockMultipartFile file =
        new MockMultipartFile("file", "shop.csv", "text/csv", CSV.getBytes(UTF_8));
    MockMultipartFile mapping =
        new MockMultipartFile("mapping", "", "application/json", MAPPING.getBytes(UTF_8));
    return multipart(path)
        .file(file)
        .file(mapping)
        .param("accountId", String.valueOf(account))
        .header(HttpHeaders.AUTHORIZATION, bearer(user));
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
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
