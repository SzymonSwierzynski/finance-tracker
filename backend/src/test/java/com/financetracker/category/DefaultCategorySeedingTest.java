package com.financetracker.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Default categories are seeded exactly once per user, tracked by a persisted flag.
 *
 * <p>The regression this pins down: seeding used to trigger whenever a user had zero categories, so
 * clearing the list and logging back in silently restored all 22 defaults. Deleting every category
 * is a choice, and the app has no business overruling it.
 */
class DefaultCategorySeedingTest extends AbstractIntegrationTest {

  @Test
  void deletedDefaultsAreNotResurrectedOnNextLogin() throws Exception {
    RegisteredUser user = register("seed-once@example.com", "password123");

    // A fresh registration gets the default tree.
    countCategories(user, 26);

    // The user clears it out entirely (deleting a parent cascades to its subcategories).
    for (JsonNode category : listCategories(user)) {
      if (category.get("parentId").isNull()) {
        mockMvc
            .perform(
                delete("/api/v1/categories/" + category.get("id").asLong())
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk());
      }
    }
    countCategories(user, 0);

    // Logging back in must respect that, not re-seed.
    RegisteredUser loggedIn = login("seed-once@example.com", "password123");
    countCategories(loggedIn, 0);
  }

  private JsonNode listCategories(RegisteredUser user) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private void countCategories(RegisteredUser user, int expected) throws Exception {
    mockMvc
        .perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(expected));
  }
}
