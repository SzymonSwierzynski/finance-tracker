package com.financetracker.rule;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Rule persistence. Every finder is user-scoped; no unscoped lookup of user data. */
public interface RuleRepository extends JpaRepository<Rule, Long> {

  /** Highest priority first, ties broken alphabetically by pattern (matches the prototype). */
  List<Rule> findByUserIdOrderByPriorityDescPatternAsc(long userId);

  Optional<Rule> findByIdAndUserId(long id, long userId);
}
