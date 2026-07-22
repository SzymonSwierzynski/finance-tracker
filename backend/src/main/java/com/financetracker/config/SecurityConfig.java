package com.financetracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.common.security.RateLimitFilter;
import com.financetracker.common.security.RestAccessDeniedHandler;
import com.financetracker.common.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Stateless JWT resource-server security. Auth endpoints + health + OpenAPI are public; everything
 * else requires a valid access token. CSRF is disabled (token-based API); the refresh cookie is
 * protected by SameSite. Method-level checks in services are the primary authorization layer.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtDecoder jwtDecoder,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      CorsConfigurationSource corsConfigurationSource,
      RateLimitFilter rateLimitFilter)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        // After CORS so a 429 still carries the headers the browser needs to surface it as a real
        // response rather than an opaque network failure; before authentication so a throttled
        // request never pays for a BCrypt hash.
        .addFilterAfter(rateLimitFilter, CorsFilter.class)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.decoder(jwtDecoder))
                    .authenticationEntryPoint(authenticationEntryPoint))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                    .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                    .frameOptions(frame -> frame.deny()));
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    // BCrypt strength 12 per §5.
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  RateLimitFilter rateLimitFilter(RateLimitProperties props, ObjectMapper objectMapper) {
    return new RateLimitFilter(props, objectMapper);
  }

  /**
   * Boot auto-registers any {@code Filter} bean into the servlet chain. We want the rate limiter
   * only where it is placed above (inside the security chain, after CORS), so the automatic
   * registration is switched off to avoid running it twice in the wrong position.
   */
  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(props.allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
