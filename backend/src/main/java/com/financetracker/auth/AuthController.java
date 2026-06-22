package com.financetracker.auth;

import com.financetracker.auth.dto.LoginRequest;
import com.financetracker.auth.dto.RegisterRequest;
import com.financetracker.auth.dto.TokenResponse;
import com.financetracker.auth.dto.UserProfileResponse;
import com.financetracker.common.security.AuthUser;
import com.financetracker.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final RefreshCookieService refreshCookieService;

  public AuthController(AuthService authService, RefreshCookieService refreshCookieService) {
    this.authService = authService;
    this.refreshCookieService = refreshCookieService;
  }

  @PostMapping("/register")
  public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
    AuthService.AuthResult result =
        authService.register(request.email(), request.password(), request.displayName());
    return tokenResponse(result, HttpStatus.CREATED);
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthService.AuthResult result = authService.login(request.email(), request.password());
    return tokenResponse(result, HttpStatus.OK);
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      @CookieValue(name = "${app.auth.cookie.name:refreshToken}", required = false)
          String refreshToken) {
    AuthService.AuthResult result = authService.refresh(refreshToken);
    return tokenResponse(result, HttpStatus.OK);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "${app.auth.cookie.name:refreshToken}", required = false)
          String refreshToken) {
    authService.logout(refreshToken);
    ResponseCookie cleared = refreshCookieService.clear();
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
  }

  @GetMapping("/me")
  @SecurityRequirement(name = "bearer-jwt")
  public UserProfileResponse me(@CurrentUser AuthUser user) {
    return authService.profile(user.id());
  }

  private ResponseEntity<TokenResponse> tokenResponse(
      AuthService.AuthResult result, HttpStatus status) {
    ResponseCookie cookie = refreshCookieService.create(result.rawRefreshToken());
    TokenResponse body =
        new TokenResponse(
            result.accessToken(), "Bearer", result.accessTokenExpiresInSeconds(), result.user());
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(body);
  }
}
