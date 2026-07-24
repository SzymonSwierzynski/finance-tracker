package com.financetracker.export.dto;

/**
 * Outcome of restoring a backup (Phase 6). Restore is additive and idempotent: accounts and
 * categories are matched by name (reused if present, else created), and transactions are deduped by
 * hash, so re-running the same backup imports nothing new.
 *
 * @param accountsCreated accounts created (existing same-named accounts are reused, not counted)
 * @param categoriesCreated categories created (existing same-named ones reused, not counted)
 * @param transactionsImported transactions inserted
 * @param transactionsSkipped transactions skipped as duplicates (or whose account couldn't resolve)
 * @param transfersSkipped transfer rows skipped — the backup format omits the counter-account
 */
public record RestoreSummary(
    int accountsCreated,
    int categoriesCreated,
    int transactionsImported,
    int transactionsSkipped,
    int transfersSkipped) {}
