package com.financetracker.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: full Spring context + MockMvc against a real (Testcontainers)
 * Postgres. One container is shared across all subclasses (singleton pattern).
 *
 * <p>Rate limiting is off here: every test registers users from the same loopback address, so the
 * suite would otherwise throttle itself. {@link com.financetracker.auth.RateLimitTest} turns it
 * back on with its own budget to prove the filter works.
 */
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

  @ServiceConnection
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  /** Registers a user and returns their id, access token and refresh cookie. */
  protected RegisteredUser register(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return new RegisteredUser(
        body.get("user").get("id").asLong(),
        email,
        body.get("accessToken").asText(),
        result.getResponse().getCookie("refreshToken"));
  }

  /** Logs an existing user in and returns their id, access token and refresh cookie. */
  protected RegisteredUser login(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return new RegisteredUser(
        body.get("user").get("id").asLong(),
        email,
        body.get("accessToken").asText(),
        result.getResponse().getCookie("refreshToken"));
  }

  protected record RegisteredUser(
      long id, String email, String accessToken, Cookie refreshCookie) {}

  /** Authorization header value for a registered user. */
  protected static String bearer(RegisteredUser user) {
    return "Bearer " + user.accessToken();
  }

  /**
   * Removes the categories the default seeder creates on registration, so a test can assert against
   * a clean slate. Deleting a top-level category cascades to its children, so only parents are
   * deleted here.
   */
  protected void clearCategories(RegisteredUser user) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    for (JsonNode category : objectMapper.readTree(result.getResponse().getContentAsString())) {
      if (category.get("parentId").isNull()) {
        mockMvc
            .perform(
                delete("/api/v1/categories/" + category.get("id").asLong())
                    .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk());
      }
    }
  }
}
