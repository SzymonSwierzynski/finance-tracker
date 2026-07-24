package com.financetracker.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.HttpHeaders;

/**
 * Observability wiring: a correlation id on every response (generated, echoed, and sanitized
 * against log forging), and a Prometheus scrape endpoint exposing JVM plus custom business metrics.
 *
 * <p>{@code @AutoConfigureObservability} re-enables metrics export, which {@code @SpringBootTest}
 * switches off by default — without it the Prometheus registry (and its scrape endpoint) is absent.
 */
@AutoConfigureObservability
class ObservabilityTest extends AbstractIntegrationTest {

  @Test
  void tagsEveryResponseWithAGeneratedCorrelationId() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"));
  }

  @Test
  void echoesAProvidedCorrelationId() throws Exception {
    mockMvc
        .perform(get("/actuator/health").header("X-Request-Id", "trace-abc-123"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "trace-abc-123"));
  }

  @Test
  void sanitizesAForgedCorrelationId() throws Exception {
    // Characters outside the id charset (here a space and '!') are stripped, so a caller can't
    // forge log lines via a crafted header.
    mockMvc
        .perform(get("/actuator/health").header("X-Request-Id", "a b!c"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "abc"));
  }

  @Test
  void prometheusRequiresAuthentication() throws Exception {
    // Metrics are operational intel (registration/txn volume) — not public.
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
  }

  @Test
  void prometheusExposesJvmAndBusinessMetrics() throws Exception {
    RegisteredUser user = register("obs-metrics@example.com", "password123"); // bumps registrations

    String body =
        mockMvc
            .perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .contains("jvm_memory_used_bytes")
        .contains("financetracker_registrations_total")
        .contains("financetracker_transactions_entered_total");
  }
}
