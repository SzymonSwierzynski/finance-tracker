package com.financetracker.rule;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Auto-categorization matcher — a faithful port of the prototype's {@code lib/rules.ts}. Rules are
 * tried highest-priority first; a rule matches when its (non-empty, trimmed) pattern is a
 * case-insensitive substring of the description. Pure and free of the persistence layer so the
 * rules service and the CSV import pipeline share one implementation.
 */
public final class RuleMatcher {

  private RuleMatcher() {}

  /** A structural rule: anything with a pattern, a target category and a priority. */
  public record MatchableRule(String pattern, long categoryId, int priority) {}

  /** The category id for a description, or {@code null} when nothing matches. */
  public static Long match(String description, List<MatchableRule> rules) {
    String haystack = (description == null ? "" : description).toLowerCase(Locale.ROOT);
    List<MatchableRule> byPriority =
        rules.stream().sorted(Comparator.comparingInt(MatchableRule::priority).reversed()).toList();
    for (MatchableRule rule : byPriority) {
      String needle = rule.pattern().trim().toLowerCase(Locale.ROOT);
      if (!needle.isEmpty() && haystack.contains(needle)) {
        return rule.categoryId();
      }
    }
    return null;
  }
}
