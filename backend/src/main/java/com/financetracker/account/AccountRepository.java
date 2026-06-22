package com.financetracker.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Account persistence. Every finder is user-scoped: there is intentionally no method that loads an
 * account by id alone, so a service can never reach another user's row.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

  List<Account> findByUserIdOrderByNameAsc(long userId);

  List<Account> findByUserIdAndArchivedFalseOrderByNameAsc(long userId);

  Optional<Account> findByIdAndUserId(long id, long userId);

  boolean existsByIdAndUserId(long id, long userId);
}
