package com.financetracker.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Revokes every live token for one user — the response to detected token reuse. Returns how many
   * sessions were killed (logged as a security event).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
  int revokeAllForUser(@Param("userId") long userId, @Param("now") Instant now);

  /**
   * Deletes tokens past their expiry. Revoked-but-unexpired rows are deliberately kept: they are
   * what makes reuse detection possible, and they stop being useful once they expire anyway.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
