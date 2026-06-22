package com.financetracker.reporting;

import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.reporting.dto.SummaryResponse;
import com.financetracker.settings.SettingsService;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionRepository.SummaryRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read models for "where my money goes". Totals are computed in SQL and returned in base-currency
 * minor units; correctness is pinned by fixed-fixture tests asserting to the grosz.
 */
@Service
public class ReportingService {

  private final TransactionRepository transactionRepository;
  private final SettingsService settingsService;

  public ReportingService(
      TransactionRepository transactionRepository, SettingsService settingsService) {
    this.transactionRepository = transactionRepository;
    this.settingsService = settingsService;
  }

  @Transactional(readOnly = true)
  public SummaryResponse summary(long userId, LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new UnprocessableEntityException("'from' must not be after 'to'.");
    }
    List<SummaryRow> rows = transactionRepository.summarize(userId, from, to);

    long incomeMinor = 0L;
    long expenseMinor = 0L;
    for (SummaryRow row : rows) {
      long base = row.getBaseMinor().setScale(0).longValueExact();
      if ("income".equals(row.getType())) {
        incomeMinor = base;
      } else if ("expense".equals(row.getType())) {
        expenseMinor = base;
      }
    }

    String currency = settingsService.reportingCurrency(userId);
    return new SummaryResponse(
        from, to, currency, incomeMinor, expenseMinor, incomeMinor - expenseMinor);
  }
}
