package com.financetracker.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Smart CSV import: the mBank-style fixture auto-detects and commits with correct signs/sums. */
class ImportSmartDetectionTest extends AbstractIntegrationTest {

  private byte[] fixture() throws Exception {
    return Files.readAllBytes(Path.of("src/test/resources/imports/mbank_sample.csv"));
  }

  private MockMultipartFile file() throws Exception {
    return new MockMultipartFile("file", "mbank_sample.csv", "text/csv", fixture());
  }

  @Test
  void previewAutoDetectsMappingAndSums() throws Exception {
    RegisteredUser user = register("smartcsv-p@example.com", "password123");
    long account = createChecking(user);

    mockMvc
        .perform(
            multipart("/api/v1/imports/preview")
                .file(file())
                .param("accountId", String.valueOf(account))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validRows").value(10))
        .andExpect(jsonPath("$.duplicateRows").value(0))
        .andExpect(jsonPath("$.incomeMinor").value(517500))
        .andExpect(jsonPath("$.expenseMinor").value(194969))
        .andExpect(jsonPath("$.detection.encoding").value("windows-1250"))
        .andExpect(jsonPath("$.detection.headerRowIndex").value(5))
        .andExpect(jsonPath("$.mapping.dateIndex").value(1))
        .andExpect(jsonPath("$.mapping.amountIndex").value(6))
        .andExpect(jsonPath("$.mapping.descriptionIndexes[0]").value(2))
        .andExpect(jsonPath("$.mapping.descriptionIndexes[1]").value(3));
  }

  @Test
  void commitAutoDetectsAndInsertsWithCorrectSigns() throws Exception {
    RegisteredUser user = register("smartcsv-c@example.com", "password123");
    long account = createChecking(user);

    mockMvc
        .perform(
            multipart("/api/v1/imports/commit")
                .file(file())
                .param("accountId", String.valueOf(account))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(10));

    var json =
        objectMapper.readTree(
            mockMvc
                .perform(
                    get("/api/v1/transactions?size=50&accountId=" + account)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long income =
        StreamSupport.stream(json.get("items").spliterator(), false)
            .filter(n -> n.get("type").asText().equals("income"))
            .count();
    assertThat(income).isEqualTo(3);
    assertThat(json.get("total").asInt()).isEqualTo(10);
  }

  @Test
  void previewIsScopedPerUser() throws Exception {
    RegisteredUser alice = register("smartcsv-a@example.com", "password123");
    RegisteredUser bob = register("smartcsv-b@example.com", "password123");
    long acct = createChecking(alice);
    mockMvc
        .perform(
            multipart("/api/v1/imports/preview")
                .file(file())
                .param("accountId", String.valueOf(acct))
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
  }

  private long createChecking(RegisteredUser user) throws Exception {
    var result =
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
