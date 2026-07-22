package com.financetracker.importing;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Import-profile persistence (one per user+account). User-scoped finders only. */
public interface ImportProfileRepository extends JpaRepository<ImportProfile, Long> {

  Optional<ImportProfile> findByUserIdAndAccountId(long userId, long accountId);
}
