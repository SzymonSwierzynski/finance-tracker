package com.financetracker.fx;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder is scoped by {@code userId}; there is no unscoped lookup of a user's rates. */
public interface FxRateRepository extends JpaRepository<FxRate, Long> {

  List<FxRate> findByUserIdOrderByCurrencyAsc(long userId);

  Optional<FxRate> findByUserIdAndCurrency(long userId, String currency);
}
