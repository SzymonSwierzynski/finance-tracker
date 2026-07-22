package com.financetracker.settings;

import com.financetracker.common.error.NotFoundException;
import com.financetracker.settings.dto.SettingsResponse;
import com.financetracker.settings.dto.UpdateSettingsRequest;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and updates per-user settings; creates the default row at registration. */
@Service
public class SettingsService {

  private final SettingsRepository repository;

  public SettingsService(SettingsRepository repository) {
    this.repository = repository;
  }

  /** Creates the default settings row (reporting currency PLN) for a newly registered user. */
  @Transactional
  public void createDefault(long userId) {
    Settings settings = new Settings();
    settings.setUserId(userId);
    settings.setReportingCurrency("PLN");
    repository.save(settings);
  }

  @Transactional(readOnly = true)
  public SettingsResponse get(long userId) {
    return repository
        .findById(userId)
        .map(s -> new SettingsResponse(s.getReportingCurrency()))
        .orElseThrow(() -> NotFoundException.of("Settings", userId));
  }

  /**
   * Claims the one-time right to seed default categories for this user, marking it done. Returns
   * false when seeding has already happened, so a user who deleted the defaults never gets them
   * back. Runs inside the caller's transaction, so the claim and the seed commit together; a
   * concurrent double-claim loses on the optimistic-lock version rather than double-seeding.
   */
  @Transactional
  public boolean claimCategorySeeding(long userId) {
    Settings settings =
        repository.findById(userId).orElseThrow(() -> NotFoundException.of("Settings", userId));
    if (settings.getCategoriesSeededAt() != null) {
      return false;
    }
    settings.setCategoriesSeededAt(Instant.now());
    repository.save(settings);
    return true;
  }

  /** The user's base/reporting currency (ISO 4217). Used to resolve transaction rates to base. */
  @Transactional(readOnly = true)
  public String reportingCurrency(long userId) {
    return repository
        .findById(userId)
        .map(Settings::getReportingCurrency)
        .orElseThrow(() -> NotFoundException.of("Settings", userId));
  }

  @Transactional
  public SettingsResponse update(long userId, UpdateSettingsRequest request) {
    Settings settings =
        repository.findById(userId).orElseThrow(() -> NotFoundException.of("Settings", userId));
    settings.setReportingCurrency(request.reportingCurrency().toUpperCase(Locale.ROOT));
    Settings saved = repository.save(settings);
    return new SettingsResponse(saved.getReportingCurrency());
  }
}
