package com.financetracker.fx;

import com.financetracker.common.UserOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A user-maintained exchange rate from one currency into that user's reporting (base) currency.
 *
 * <p>This table is the <em>source</em> of the rate a transaction locks at entry time — it is never
 * consulted again afterwards. Editing a rate here changes what future transactions lock; it never
 * rewrites history (CLAUDE.md §7).
 *
 * <p>{@code baseCurrency} records what the rate was anchored to when it was saved, so that changing
 * the reporting currency makes stored rates visibly stale rather than silently wrong.
 */
@Entity
@Table(name = "fx_rates")
@Getter
@Setter
public class FxRate extends UserOwnedEntity {

  @Column(name = "currency", nullable = false)
  private String currency;

  @Column(name = "rate_to_base", nullable = false)
  private BigDecimal rateToBase;

  @Column(name = "base_currency", nullable = false)
  private String baseCurrency;

  @Column(name = "source")
  private String source;

  @Column(name = "last_fetched_at")
  private Instant lastFetchedAt;

  /** True when this rate is anchored to a base the user no longer reports in. */
  public boolean isStaleAgainst(String currentBaseCurrency) {
    return !baseCurrency.equalsIgnoreCase(currentBaseCurrency);
  }
}
