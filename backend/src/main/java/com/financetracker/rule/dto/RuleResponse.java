package com.financetracker.rule.dto;

/** Rule as exposed to clients. */
public record RuleResponse(long id, String pattern, long categoryId, int priority, long version) {}
