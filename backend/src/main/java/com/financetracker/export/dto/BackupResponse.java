package com.financetracker.export.dto;

import java.util.List;

/**
 * A full JSON snapshot of a user's core financial data (Phase 6 backup). References are by name
 * (accounts, category parents) so a restore can remap ids — money stays integer minor units and
 * {@code rateToBase} is preserved, so the data round-trips losslessly.
 *
 * <p>Restore (re-import) is a planned follow-up; this endpoint provides the backup half.
 */
public record BackupResponse(
    String reportingCurrency,
    List<AccountBackup> accounts,
    List<CategoryBackup> categories,
    List<ExportedTransaction> transactions) {

  public record AccountBackup(
      String name,
      String type,
      String currency,
      Long startingBalanceMinor,
      boolean trackBalance,
      boolean archived) {}

  /** {@code parent} is the parent category's name, or null for a top-level category. */
  public record CategoryBackup(String name, String kind, String parent, String color) {}
}
