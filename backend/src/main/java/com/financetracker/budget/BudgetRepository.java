package com.financetracker.budget;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Budget persistence. Every finder is user-scoped; no unscoped lookup of user data. */
public interface BudgetRepository extends JpaRepository<Budget, Long> {

  List<Budget> findByUserId(long userId);

  Optional<Budget> findByIdAndUserId(long id, long userId);

  boolean existsByUserIdAndCategoryId(long userId, long categoryId);
}
