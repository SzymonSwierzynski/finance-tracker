package com.financetracker.common.security;

import static com.financetracker.common.error.ProblemSupport.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client throttle on the unauthenticated auth endpoints (CLAUDE.md §5). Runs inside the
 * security chain but ahead of authentication, so a rejected request never reaches the BCrypt hash.
 *
 * <p>State is a per-instance in-memory token bucket. That is deliberately the cheap option: it
 * needs no extra infrastructure and the backend stays stateless with respect to <em>user</em>
 * state. The tradeoff is that the effective limit multiplies by the number of instances, so a
 * horizontally scaled deployment should move the bucket store behind Redis — {@link #tryConsume} is
 * the only seam that has to change.
 *
 * <p>The key is client IP + path. Behind a proxy this relies on {@code
 * server.forward-headers-strategy} (set in the prod profile) to make {@code getRemoteAddr()} report
 * the real client rather than the load balancer. Note that NAT'd clients share a bucket, which is
 * why the default capacity is generous rather than tight.
 *
 * <p>Declared as a {@code @Bean} in {@code SecurityConfig} rather than component-scanned: web slice
 * tests pull in {@code Filter} beans automatically, and this one has no business running there.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  /** Unauthenticated, credential-bearing, or session-minting — the endpoints worth throttling. */
  private static final Set<String> LIMITED_PATHS =
      Set.of("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh");

  private final RateLimitProperties props;
  private final ObjectMapper objectMapper;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RateLimitFilter(RateLimitProperties props, ObjectMapper objectMapper) {
    this.props = props;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !props.enabled() || !LIMITED_PATHS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String key = clientAddress(request) + "|" + request.getRequestURI();
    if (tryConsume(key)) {
      chain.doFilter(request, response);
      return;
    }

    long retryAfterSeconds = Math.max(1, props.window().toSeconds() / props.capacity());
    // Log the path, never the body — credentials must not reach the logs.
    log.warn("Rate limit exceeded for {} on {}", clientAddress(request), request.getRequestURI());

    ProblemDetail body =
        problem(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests",
            "Too many attempts. Please wait a moment and try again.",
            request.getRequestURI());

    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    objectMapper.writeValue(response.getOutputStream(), body);
  }

  /**
   * The client address to throttle on.
   *
   * <p>This deliberately does NOT parse {@code X-Forwarded-For} itself. Spring's
   * {@code ForwardedHeaderFilter} runs first and its request wrapper HIDES the forwarded headers
   * ({@code getHeader("X-Forwarded-For")} returns null downstream), so a filter here cannot see
   * them — and its {@code getRemoteAddr()} reports the LEFTMOST entry, which the client controls.
   *
   * <p>Correctness therefore comes from configuration, not code: the prod profile uses
   * {@code server.forward-headers-strategy=native}, which puts Tomcat's {@code RemoteIpValve} in
   * charge. The valve walks the header from the RIGHT, skipping hops that match
   * {@code internal-proxies}, so the address here is the real peer and a forged value — which can
   * only ever land further left — never reaches it.
   */
  private static String clientAddress(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  /** Takes one token for {@code key}, refilling continuously at capacity-per-window. */
  private boolean tryConsume(String key) {
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(props.capacity()));
    return bucket.tryConsume(System.nanoTime(), props.capacity(), props.window().toNanos());
  }

  /**
   * Drops buckets nobody has touched for a while. Without this the map is itself a slow memory-leak
   * vector — an attacker rotating source addresses would grow it without bound.
   */
  @Scheduled(fixedDelayString = "${app.rate-limit.sweep-interval:PT10M}")
  public void evictIdleBuckets() {
    long staleBefore = System.nanoTime() - (props.window().toNanos() * 2);
    buckets.entrySet().removeIf(e -> e.getValue().lastSeenBefore(staleBefore));
  }

  /**
   * A continuously-refilling token bucket. Synchronized per key: contention is limited to requests
   * from the same client hitting the same endpoint, which is exactly the case we are rate limiting.
   */
  private static final class Bucket {
    private double tokens;
    private long lastRefillNanos = System.nanoTime();

    private Bucket(int capacity) {
      this.tokens = capacity;
    }

    private synchronized boolean tryConsume(long nowNanos, int capacity, long windowNanos) {
      double refill = ((double) (nowNanos - lastRefillNanos) / windowNanos) * capacity;
      tokens = Math.min(capacity, tokens + refill);
      lastRefillNanos = nowNanos;
      if (tokens >= 1.0d) {
        tokens -= 1.0d;
        return true;
      }
      return false;
    }

    private synchronized boolean lastSeenBefore(long nanos) {
      return lastRefillNanos < nanos;
    }
  }
}
