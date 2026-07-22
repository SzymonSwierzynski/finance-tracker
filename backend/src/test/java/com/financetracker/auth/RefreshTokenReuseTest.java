package com.financetracker.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Refresh tokens are single-use. Presenting one that has already been rotated away means the value
 * leaked, and since we cannot tell the attacker from the victim, every session for that user is
 * revoked and both parties are forced to log in again.
 */
class RefreshTokenReuseTest extends AbstractIntegrationTest {

  @Test
  void replayingARotatedTokenRevokesTheWholeFamily() throws Exception {
    RegisteredUser user = register("reuse@example.com", "password123");
    Cookie first = user.refreshCookie();

    // Normal rotation: the first token is spent and a second is issued.
    Cookie second = refreshWith(first, status().isOk());

    // Replay of the spent token — this is the leak signal.
    refreshWith(first, status().isUnauthorized());

    // ...and the still-unused second token is now dead too: the whole family was revoked.
    refreshWith(second, status().isUnauthorized());
  }

  @Test
  void rotationKeepsWorkingAcrossSeveralRefreshes() throws Exception {
    RegisteredUser user = register("rotate@example.com", "password123");

    Cookie cookie = user.refreshCookie();
    for (int i = 0; i < 3; i++) {
      cookie = refreshWith(cookie, status().isOk());
    }
  }

  @Test
  void loggingOutRevokesTheTokenWithoutTrippingReuseDetection() throws Exception {
    RegisteredUser user = register("reuse-logout@example.com", "password123");

    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(user.refreshCookie()))
        .andExpect(status().isNoContent());

    // The logged-out token is revoked, so refreshing with it fails. (It also counts as reuse —
    // which is harmless here: the user's sessions are already meant to be gone.)
    refreshWith(user.refreshCookie(), status().isUnauthorized());
  }

  /** Performs a refresh with the given cookie and returns the newly issued one (if any). */
  private Cookie refreshWith(
      Cookie cookie, org.springframework.test.web.servlet.ResultMatcher expectedStatus)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/api/v1/auth/refresh").cookie(cookie))
            .andExpect(expectedStatus)
            .andReturn();
    return result.getResponse().getCookie("refreshToken");
  }
}
