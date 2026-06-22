package com.financetracker.auth;

import com.financetracker.config.AuthProperties;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Mints short-lived RS256 access tokens (subject = user id, {@code email} claim, {@code kid}). */
@Service
public class JwtService {

  private final JwtEncoder jwtEncoder;
  private final AuthProperties props;

  public JwtService(JwtEncoder jwtEncoder, AuthProperties props) {
    this.jwtEncoder = jwtEncoder;
    this.props = props;
  }

  public AccessToken issue(long userId, String email) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(props.accessTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(props.issuer())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(String.valueOf(userId))
            .claim("email", email)
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(props.jwt().keyId()).build();
    String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new AccessToken(value, props.accessTokenTtl().toSeconds());
  }

  /** A minted access token and its lifetime in seconds. */
  public record AccessToken(String value, long expiresInSeconds) {}
}
