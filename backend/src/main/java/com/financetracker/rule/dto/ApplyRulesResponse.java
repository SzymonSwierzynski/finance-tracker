package com.financetracker.rule.dto;

/**
 * Result of re-running rules over uncategorized transactions: how many were scanned and how many
 * were newly categorized.
 */
public record ApplyRulesResponse(int scanned, int categorized) {}
