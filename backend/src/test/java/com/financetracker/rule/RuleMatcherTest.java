package com.financetracker.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.rule.RuleMatcher.MatchableRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ported from the prototype's {@code lib/rules.test.ts}: substring, case-insensitivity, priority.
 */
class RuleMatcherTest {

  // Category ids stand in for the prototype's string ids: groceries=1, fuel=2, travel=3,
  // shopping=4.
  private static final List<MatchableRule> RULES =
      List.of(
          new MatchableRule("biedronka", 1L, 1),
          new MatchableRule("orlen", 2L, 1),
          new MatchableRule("orlen station", 3L, 5),
          new MatchableRule("shop", 4L, 0));

  @Test
  void matchesCaseInsensitivelyAsSubstring() {
    assertThat(RuleMatcher.match("Płatność BIEDRONKA 4012", RULES)).isEqualTo(1L);
  }

  @Test
  void prefersTheHigherPriorityRuleOnOverlap() {
    // Contains both "orlen" (priority 1) and "orlen station" (priority 5).
    assertThat(RuleMatcher.match("ORLEN STATION 22", RULES)).isEqualTo(3L);
  }

  @Test
  void returnsNullWhenNothingMatches() {
    assertThat(RuleMatcher.match("Unknown vendor", RULES)).isNull();
  }

  @Test
  void ignoresEmptyPatterns() {
    assertThat(RuleMatcher.match("anything", List.of(new MatchableRule("  ", 9L, 9)))).isNull();
  }
}
