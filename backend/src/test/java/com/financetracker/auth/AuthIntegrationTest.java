package com.financetracker.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * End-to-end auth flow against real Postgres: register -> me -> login -> refresh(rotate) -> logout.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

  @Test
  void registerLoginMeRefreshLogoutFlow() throws Exception {
    RegisteredUser user = register("flow@example.com", "password123");

    // Protected endpoint with the access token.
    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("flow@example.com"))
        .andExpect(jsonPath("$.id").value(user.id()));

    // No token / bad token -> 401.
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
        .andExpect(status().isUnauthorized());

    // Login issues a fresh token + rotated cookie.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"flow@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(cookie().exists("refreshToken"));

    // Refresh with the register cookie works once...
    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());

    // ...and the same (now rotated/revoked) token cannot be reused.
    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginWithWrongPasswordReturns401() throws Exception {
    register("wrongpass@example.com", "password123");
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wrongpass@example.com\",\"password\":\"nope12345\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void duplicateEmailReturns409() throws Exception {
    register("dupe@example.com", "password123");
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dupe@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void registerSeedsDefaultCategories() throws Exception {
    RegisteredUser user = register("seed@example.com", "password123");

    mockMvc
        .perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(22))
        .andExpect(jsonPath("$[?(@.name == 'Groceries')].kind").value("expense"));
  }

  @Test
  void logoutRevokesRefreshToken() throws Exception {
    RegisteredUser user = register("logout@example.com", "password123");

    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(user.refreshCookie()))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refreshToken", 0));

    // The revoked token can no longer refresh.
    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()))
        .andExpect(status().isUnauthorized());
  }
}
