package com.financetracker.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Unit-level checks of the problem+json mapping. */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void notFoundMapsTo404WithInstance() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/things/9");

    ProblemDetail problem = handler.handleNotFound(new NotFoundException("nope"), request);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problem.getTitle()).isEqualTo("Not found");
    assertThat(problem.getInstance()).hasToString("/api/v1/things/9");
  }

  @Test
  void unmappedPathMapsTo404NotAnUnexpected500() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/actuator/prometheus");

    ProblemDetail problem =
        handler.handleNoResource(
            new NoResourceFoundException(HttpMethod.GET, "actuator/prometheus"), request);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problem.getProperties()).doesNotContainKey("errorId");
  }

  @Test
  void unexpectedHidesDetailAndAttachesQuotableErrorId() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/boom");

    ProblemDetail problem =
        handler.handleUnexpected(new RuntimeException("secret stack detail"), request);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problem.getDetail()).doesNotContain("secret stack detail");
    assertThat(problem.getProperties()).containsKey("errorId");
  }
}
