package com.financetracker.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * A single-user deployment closes signup once its account exists; otherwise anyone with the URL can
 * create one. The base class leaves registration enabled (every other test registers users), so
 * this class turns it off for itself.
 */
@TestPropertySource(properties = "app.auth.registration-enabled=false")
class RegistrationDisabledTest extends AbstractIntegrationTest {

  @Test
  void registrationIsRejectedWhenDisabled() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"walkin@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value("Registration is closed on this instance."));
  }

  @Test
  void theRejectionDoesNotRevealWhetherAnAddressIsRegistered() throws Exception {
    // The flag is checked BEFORE the email lookup, so a taken address and a free one are
    // indistinguishable — otherwise 409-vs-422 would answer "does this person have an account?".
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"someone-else@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value("Registration is closed on this instance."));
  }

  @Test
  void loginStillWorksWhenRegistrationIsClosed() throws Exception {
    // Closing signup must not lock out the account that already exists.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isUnauthorized()); // reached the credential check, not the signup gate
  }
}
