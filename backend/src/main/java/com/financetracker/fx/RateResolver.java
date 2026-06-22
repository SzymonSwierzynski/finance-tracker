package com.financetracker.fx;

import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.settings.SettingsService;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Resolves the {@code rateToBase} locked onto a transaction at entry time (CLAUDE.md §7).
 *
 * <ul>
 *   <li>A client-supplied positive rate always wins (and is stored verbatim).
 *   <li>An amount already in the reporting currency resolves to 1.
 *   <li>Anything else is rejected with 422 until the user-managed FX rate table arrives (Phase 3) —
 *       the contract forbids guessing or live-converting historical totals.
 * </ul>
 */
@Service
public class RateResolver {

  private final SettingsService settingsService;

  public RateResolver(SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  public BigDecimal resolve(long userId, String currency, BigDecimal supplied) {
    if (supplied != null) {
      if (supplied.signum() <= 0) {
        throw new UnprocessableEntityException("rateToBase must be a positive number.");
      }
      return supplied;
    }
    String base = settingsService.reportingCurrency(userId);
    if (currency.equalsIgnoreCase(base)) {
      return BigDecimal.ONE;
    }
    throw new UnprocessableEntityException(
        "No exchange rate to base for " + currency + "; provide rateToBase.");
  }
}
