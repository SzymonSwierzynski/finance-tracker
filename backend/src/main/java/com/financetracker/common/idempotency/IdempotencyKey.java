package com.financetracker.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A claimed idempotency key. Standalone (not {@code UserOwnedEntity}) — write-once, no optimistic
 * lock or updated_at needed. {@code responseBody} is the JSON of the first successful response,
 * replayed verbatim on a repeat with a matching fingerprint.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
public class IdempotencyKey {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private String scope;

  @Column(name = "idempotency_key", nullable = false)
  private String key;

  @Column(name = "request_fingerprint", nullable = false)
  private String fingerprint;

  @Column(name = "response_body")
  private String responseBody;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;
}
