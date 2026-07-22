package com.financetracker.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The auth throttle. The base class disables rate limiting (the suite would otherwise throttle
 * itself, since every test registers from the same loopback address); this class turns it back on
 * with a tiny budget so the behaviour is actually exercised.
 *
 * <p>Buckets live in a singleton filter shared by every test in the context, so each test uses its
 * own source address — otherwise the tests would consume each other's budget and pass or fail
 * depending on execution order.
 */
@TestPropertySource(
    properties = {
      "app.rate-limit.enabled=true",
      "app.rate-limit.capacity=3",
      "app.rate-limit.window=1m"
    })
class RateLimitTest extends AbstractIntegrationTest {

  @Test
  void repeatedLoginAttemptsAreThrottledWithProblemJson() throws Exception {
    RequestPostProcessor client = from("203.0.113.10");

    // Unknown credentials: rejected at the lookup, so this measures the throttle and not BCrypt.
    for (int i = 0; i < 3; i++) {
      attemptLogin(client).andExpect(status().isUnauthorized());
    }

    attemptLogin(client)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.title").value("Too many requests"));
  }

  @Test
  void throttlingIsPerClientAddress() throws Exception {
    RequestPostProcessor noisy = from("203.0.113.20");
    RequestPostProcessor quiet = from("203.0.113.21");

    for (int i = 0; i < 4; i++) {
      attemptLogin(noisy);
    }
    attemptLogin(noisy).andExpect(status().isTooManyRequests());

    // An unrelated client is unaffected by the noisy one's budget.
    attemptLogin(quiet).andExpect(status().isUnauthorized());
  }

  @Test
  void bucketsAreScopedPerEndpoint() throws Exception {
    RequestPostProcessor client = from("203.0.113.30");

    for (int i = 0; i < 4; i++) {
      attemptLogin(client);
    }

    // Exhausting /login must not lock the same client out of /refresh — separate buckets.
    mockMvc
        .perform(post("/api/v1/auth/refresh").with(client))
        .andExpect(status().isUnauthorized()); // no cookie, but NOT 429
  }

  @Test
  void authenticatedEndpointsAreNotThrottled() throws Exception {
    RegisteredUser user = register("ratelimit-ok@example.com", "password123");
    RequestPostProcessor client = from("203.0.113.40");

    // Well past the auth budget: only the unauthenticated auth endpoints are limited.
    for (int i = 0; i < 10; i++) {
      mockMvc
          .perform(
              get("/api/v1/settings").with(client).header(HttpHeaders.AUTHORIZATION, bearer(user)))
          .andExpect(status().isOk());
    }
  }

  private ResultActions attemptLogin(RequestPostProcessor client) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login")
            .with(client)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody@example.com\",\"password\":\"password123\"}"));
  }

  /** Pins the request's source address so each test gets its own rate-limit bucket. */
  private static RequestPostProcessor from(String address) {
    return request -> {
      request.setRemoteAddr(address);
      return request;
    };
  }
}
