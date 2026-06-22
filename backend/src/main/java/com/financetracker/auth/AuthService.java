package com.financetracker.auth;

import com.financetracker.auth.dto.UserProfileResponse;
import com.financetracker.common.error.ConflictException;
import com.financetracker.common.error.NotFoundException;
import com.financetracker.config.AuthProperties;
import com.financetracker.settings.SettingsService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication orchestration: registration, login, refresh-token rotation and logout. Refresh
 * tokens are opaque random values; only their SHA-256 hash is persisted so they can be revoked.
 */
@Service
public class AuthService {

  private static final int REFRESH_TOKEN_BYTES = 32;

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final SettingsService settingsService;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthProperties props;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      SettingsService settingsService,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuthProperties props) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.settingsService = settingsService;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.props = props;
  }

  @Transactional
  public AuthResult register(String email, String rawPassword, String displayName) {
    String normalizedEmail = normalizeEmail(email);
    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new ConflictException("Email is already registered");
    }
    User user = new User();
    user.setEmail(normalizedEmail);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setDisplayName(displayName);
    user.setStatus(UserStatus.ACTIVE);
    User saved = userRepository.save(user);

    settingsService.createDefault(saved.getId());
    return issueTokens(saved);
  }

  @Transactional
  public AuthResult login(String email, String rawPassword) {
    User user =
        userRepository
            .findByEmail(normalizeEmail(email))
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid email or password");
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BadCredentialsException("Account is disabled");
    }
    return issueTokens(user);
  }

  @Transactional
  public AuthResult refresh(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw new BadCredentialsException("Missing refresh token");
    }
    RefreshToken token =
        refreshTokenRepository
            .findByTokenHash(sha256(rawRefreshToken))
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
    if (!token.isActive(Instant.now())) {
      throw new BadCredentialsException("Refresh token is expired or revoked");
    }
    // Rotate: the presented token is single-use.
    token.setRevokedAt(Instant.now());
    refreshTokenRepository.save(token);

    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
    return issueTokens(user);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return;
    }
    refreshTokenRepository
        .findByTokenHash(sha256(rawRefreshToken))
        .ifPresent(
            token -> {
              token.setRevokedAt(Instant.now());
              refreshTokenRepository.save(token);
            });
  }

  @Transactional(readOnly = true)
  public UserProfileResponse profile(long userId) {
    return userRepository
        .findById(userId)
        .map(u -> new UserProfileResponse(u.getId(), u.getEmail(), u.getDisplayName()))
        .orElseThrow(() -> NotFoundException.of("User", userId));
  }

  private AuthResult issueTokens(User user) {
    JwtService.AccessToken accessToken = jwtService.issue(user.getId(), user.getEmail());
    String rawRefreshToken = newRefreshToken();

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUserId(user.getId());
    refreshToken.setTokenHash(sha256(rawRefreshToken));
    refreshToken.setExpiresAt(Instant.now().plus(props.refreshTokenTtl()));
    refreshTokenRepository.save(refreshToken);

    return new AuthResult(
        new UserProfileResponse(user.getId(), user.getEmail(), user.getDisplayName()),
        accessToken.value(),
        accessToken.expiresInSeconds(),
        rawRefreshToken);
  }

  private String newRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** Internal result of a successful auth — primitives only, no entity leaves the service. */
  public record AuthResult(
      UserProfileResponse user,
      String accessToken,
      long accessTokenExpiresInSeconds,
      String rawRefreshToken) {}
}
