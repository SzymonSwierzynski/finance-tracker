package com.financetracker.common.idempotency;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

  /**
   * Claim the key if free. {@code ON CONFLICT DO NOTHING} does NOT abort the transaction (a raw
   * unique violation would), so the caller can safely read the existing row afterward. A concurrent
   * duplicate blocks on the unique index until the first tx commits, then gets 0 rows here. Returns
   * rows inserted: 1 = claimed, 0 = already present.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO idempotency_keys (user_id, scope, idempotency_key, request_fingerprint)
          VALUES (:userId, :scope, :key, :fingerprint)
          ON CONFLICT (user_id, scope, idempotency_key) DO NOTHING
          """,
      nativeQuery = true)
  int tryClaim(
      @Param("userId") long userId,
      @Param("scope") String scope,
      @Param("key") String key,
      @Param("fingerprint") String fingerprint);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE idempotency_keys SET response_body = :body
          WHERE user_id = :userId AND scope = :scope AND idempotency_key = :key
          """,
      nativeQuery = true)
  int storeResponse(
      @Param("userId") long userId,
      @Param("scope") String scope,
      @Param("key") String key,
      @Param("body") String body);

  Optional<IdempotencyKey> findByUserIdAndScopeAndKey(long userId, String scope, String key);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :cutoff")
  int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
