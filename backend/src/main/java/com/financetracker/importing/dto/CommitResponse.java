package com.financetracker.importing.dto;

/**
 * Result of committing an import. {@code batchId} is null when nothing was imported (no batch is
 * created for an all-duplicate/all-invalid file, matching the prototype).
 */
public record CommitResponse(
    Long batchId, int imported, int skippedDuplicates, int skippedInvalid) {}
