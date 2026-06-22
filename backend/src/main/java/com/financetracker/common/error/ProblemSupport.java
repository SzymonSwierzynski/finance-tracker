package com.financetracker.common.error;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 {@link ProblemDetail} bodies with the project's standard extension fields. */
public final class ProblemSupport {

  private ProblemSupport() {}

  public static ProblemDetail problem(HttpStatus status, String title, String detail, String path) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(title);
    pd.setType(URI.create("about:blank"));
    if (path != null) {
      pd.setInstance(URI.create(path));
    }
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }
}
