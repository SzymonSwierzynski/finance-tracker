package com.financetracker.importing.dto;

import java.time.Instant;

/** An import batch as exposed to clients. */
public record ImportBatchResponse(
    long id, long accountId, String fileName, int count, Instant createdAt) {}
