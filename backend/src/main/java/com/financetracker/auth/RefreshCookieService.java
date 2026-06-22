package com.financetracker.auth;

import com.financetracker.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Builds the HttpOnly refresh-token cookie (and its cleared form for logout). */
@Service
public class RefreshCookieService {

  private final AuthProperties props;

  public RefreshCookieService(AuthProperties props) {
    this.props = props;
  }

  public String cookieName() {
    return props.cookie().name();
  }

  public ResponseCookie create(String rawToken) {
    return base(rawToken, (int) props.refreshTokenTtl().toSeconds());
  }

  public ResponseCookie clear() {
    return base("", 0);
  }

  private ResponseCookie base(String value, int maxAgeSeconds) {
    AuthProperties.Cookie cfg = props.cookie();
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(cfg.name(), value)
            .httpOnly(true)
            .secure(cfg.secure())
            .sameSite(cfg.sameSite())
            .path(cfg.path())
            .maxAge(maxAgeSeconds);
    if (StringUtils.hasText(cfg.domain())) {
      builder.domain(cfg.domain());
    }
    return builder.build();
  }
}
