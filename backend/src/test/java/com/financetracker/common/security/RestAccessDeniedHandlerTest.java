package com.financetracker.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

/**
 * The 403 handler is rarely hit through the API (cross-user access returns 404 to avoid leaking
 * existence), so it is verified directly: an {@link AccessDeniedException} must produce a
 * problem+json 403 rather than Spring's default HTML page.
 */
class RestAccessDeniedHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void writesProblemJson403() throws Exception {
    RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("denied"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(body.get("status").asInt()).isEqualTo(403);
    assertThat(body.get("title").asText()).isEqualTo("Forbidden");
    assertThat(body.get("detail").asText()).contains("access");
  }
}
