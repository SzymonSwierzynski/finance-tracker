package com.financetracker.fx;

import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.settings.SettingsService;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Resolves the {@code rateToBase} locked onto a transaction at entry time (CLAUDE.md §7).
 *
 * <p>Resolution order:
 *
 * <ul>
 *   <li>A client-supplied positive rate always wins (and is stored verbatim) — an import, or a user
 *       reading their statement, may know the rate the bank actually applied, which beats any
 *       table.
 *   <li>An amount already in the reporting currency resolves to 1.
 *   <li>Otherwise the user's FX rate table is consulted, ignoring rates anchored to a base they no
 *       longer report in.
 *   <li>Failing all of that, 422. The contract forbids guessing: a wrong rate here is not a
 *       transient display bug, it is a permanently wrong base amount frozen onto a transaction.
 * </ul>
 */
@Service
public class RateResolver {

  private final SettingsService settingsService;
  private final FxRateService fxRateService;

  public RateResolver(SettingsService settingsService, FxRateService fxRateService) {
    this.settingsService = settingsService;
    this.fxRateService = fxRateService;
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

    return fxRateService
        .findUsableRate(userId, currency, base)
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "No usable exchange rate from "
                        + currency
                        + " to "
                        + base
                        + ". Add one under Settings, or send an explicit rateToBase."));
  }
}
