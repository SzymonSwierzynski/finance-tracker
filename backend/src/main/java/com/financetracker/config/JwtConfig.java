package com.financetracker.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

/**
 * Access-token signing/verification. RSA with a {@code kid} header gives a key-rotation seam (§5).
 * A stable keypair must be configured in prod; dev/test generate an ephemeral key at startup.
 */
@Configuration
public class JwtConfig {

  private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

  @Bean
  public RSAKey rsaKey(AuthProperties props) {
    AuthProperties.Jwt cfg = props.jwt();
    if (StringUtils.hasText(cfg.privateKey()) && StringUtils.hasText(cfg.publicKey())) {
      return new RSAKey.Builder(RsaKeys.parsePublic(cfg.publicKey()))
          .privateKey(RsaKeys.parsePrivate(cfg.privateKey()))
          .keyID(cfg.keyId())
          .build();
    }
    log.warn(
        "No RSA JWT key configured (app.auth.jwt.private-key/public-key); generated an ephemeral "
            + "key '{}'. Tokens will not survive a restart — configure a stable keypair in prod.",
        cfg.keyId());
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
          .privateKey(pair.getPrivate())
          .keyID(cfg.keyId())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate RSA key", e);
    }
  }

  @Bean
  public JwtEncoder jwtEncoder(RSAKey rsaKey) {
    JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(rsaKey));
    return new NimbusJwtEncoder(jwks);
  }

  @Bean
  public JwtDecoder jwtDecoder(RSAKey rsaKey, AuthProperties props) {
    try {
      NimbusJwtDecoder decoder =
          NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey())
              .signatureAlgorithm(SignatureAlgorithm.RS256)
              .build();
      decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
      return decoder;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build JWT decoder", e);
    }
  }
}
