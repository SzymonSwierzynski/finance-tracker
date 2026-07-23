package com.financetracker.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tags every request with a correlation id: reuse an inbound {@code X-Request-Id} (from a gateway)
 * or mint one, put it in the log MDC ({@code requestId}) so every line for the request is
 * correlated, and echo it back so a client or operator can quote it — it is also the id the global
 * handler returns on a 500. Runs first (highest precedence) so security and rate-limit logs carry
 * it too.
 *
 * <p>An inbound id is sanitized to a short id charset before it reaches the MDC/logs, so a caller
 * can't forge log lines (CRLF injection) or bloat them.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "requestId";
  private static final int MAX_LENGTH = 64;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = sanitize(request.getHeader(HEADER));
    if (requestId == null) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  /** Keep only a safe id charset (blocks CRLF log forging); null if nothing usable remains. */
  private static String sanitize(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.length() > MAX_LENGTH ? raw.substring(0, MAX_LENGTH) : raw;
    String cleaned = trimmed.replaceAll("[^A-Za-z0-9._-]", "");
    return cleaned.isEmpty() ? null : cleaned;
  }
}
