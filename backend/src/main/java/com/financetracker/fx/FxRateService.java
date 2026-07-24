package com.financetracker.fx;

import com.financetracker.common.error.NotFoundException;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.fx.dto.FxRateResponse;
import com.financetracker.fx.dto.FxRatesResponse;
import com.financetracker.fx.dto.UpsertFxRateRequest;
import com.financetracker.settings.SettingsService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user-managed FX rate table (CLAUDE.md §7). Rates are anchored to the reporting currency, so
 * any pair cross-converts through base.
 *
 * <p>Nothing here converts historical money. These rates are only ever read at transaction-entry
 * time, where the value is copied onto the transaction and frozen; changing a rate afterwards moves
 * no past total by a single grosz.
 */
@Service
public class FxRateService {

  private final FxRateRepository repository;
  private final SettingsService settingsService;

  public FxRateService(FxRateRepository repository, SettingsService settingsService) {
    this.repository = repository;
    this.settingsService = settingsService;
  }

  @Transactional(readOnly = true)
  public FxRatesResponse list(long userId) {
    String base = settingsService.reportingCurrency(userId);
    List<FxRateResponse> rates =
        repository.findByUserIdOrderByCurrencyAsc(userId).stream()
            .map(rate -> toResponse(rate, base))
            .toList();
    return new FxRatesResponse(base, rates);
  }

  @Transactional
  public FxRateResponse upsert(long userId, String rawCurrency, UpsertFxRateRequest request) {
    String currency = normalize(rawCurrency);
    String base = settingsService.reportingCurrency(userId);

    if (currency.equals(base)) {
      throw new UnprocessableEntityException(
          "The reporting currency's rate to itself is always 1 and cannot be set.");
    }
    if (request.rateToBase().signum() <= 0) {
      throw new UnprocessableEntityException("rateToBase must be a positive number.");
    }

    FxRate rate =
        repository
            .findByUserIdAndCurrency(userId, currency)
            .orElseGet(
                () -> {
                  FxRate created = new FxRate();
                  created.setUserId(userId);
                  created.setCurrency(currency);
                  return created;
                });
    rate.setRateToBase(request.rateToBase());
    // Re-anchor on every write: whatever the user just typed is a rate against their base today.
    rate.setBaseCurrency(base);
    rate.setSource(request.source());

    return toResponse(repository.saveAndFlush(rate), base);
  }

  @Transactional
  public void delete(long userId, String rawCurrency) {
    String currency = normalize(rawCurrency);
    FxRate rate =
        repository
            .findByUserIdAndCurrency(userId, currency)
            .orElseThrow(() -> NotFoundException.of("FxRate", currency));
    repository.delete(rate);
  }

  /**
   * The rate to use when locking a transaction, or empty when there is none the resolver may trust.
   * A rate anchored to a previous reporting currency is deliberately treated as absent: using it
   * would book a wrong base amount that then becomes permanent.
   */
  @Transactional(readOnly = true)
  public Optional<BigDecimal> findUsableRate(long userId, String currency, String baseCurrency) {
    return repository
        .findByUserIdAndCurrency(userId, normalize(currency))
        .filter(rate -> !rate.isStaleAgainst(baseCurrency))
        .map(FxRate::getRateToBase);
  }

  private static FxRateResponse toResponse(FxRate rate, String currentBase) {
    return new FxRateResponse(
        rate.getCurrency(),
        rate.getRateToBase(),
        rate.getBaseCurrency(),
        rate.isStaleAgainst(currentBase),
        rate.getSource(),
        rate.getLastFetchedAt(),
        rate.getVersion());
  }

  private static String normalize(String currency) {
    return currency.trim().toUpperCase(Locale.ROOT);
  }
}
