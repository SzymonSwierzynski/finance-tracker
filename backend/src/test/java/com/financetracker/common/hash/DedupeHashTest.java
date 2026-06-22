package com.financetracker.common.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the FNV-1a port to the canonical 32-bit test vectors so the hash stays byte-identical to the
 * prototype — cross-client dedupe consistency (Phase 4 import) depends on it.
 */
class DedupeHashTest {

  @Test
  void matchesCanonicalFnv1aVectors() {
    // Standard FNV-1a 32-bit vectors (lower-case, zero-padded to 8 hex chars).
    assertThat(DedupeHash.of(List.of())).isEqualTo("811c9dc5"); // empty string -> offset basis
    assertThat(DedupeHash.of(List.of("a"))).isEqualTo("e40c292c");
    assertThat(DedupeHash.of(List.of("foobar"))).isEqualTo("bf9cf968");
  }

  @Test
  void joinsPartsWithPipeAndStringifies() {
    // "a|b" must differ from the concatenation "ab"; numbers are stringified.
    assertThat(DedupeHash.of(List.of("a", "b"))).isNotEqualTo(DedupeHash.of(List.of("ab")));
    assertThat(DedupeHash.of(List.of("1", "234")))
        .isEqualTo(DedupeHash.of(List.of(1, 234))); // 1|234 either way
  }

  @Test
  void isDeterministicAndEightHexChars() {
    String a = DedupeHash.of(List.of("2024-01-15", 1999L, "PLN", 5L, "Coffee"));
    String b = DedupeHash.of(List.of("2024-01-15", 1999L, "PLN", 5L, "Coffee"));
    assertThat(a).isEqualTo(b).hasSize(8).matches("[0-9a-f]{8}");
  }

  @Test
  void differentInputsProduceDifferentHashes() {
    String coffee = DedupeHash.of(List.of("2024-01-15", 1999L, "PLN", 5L, "Coffee"));
    String tea = DedupeHash.of(List.of("2024-01-15", 1999L, "PLN", 5L, "Tea"));
    assertThat(coffee).isNotEqualTo(tea);
  }
}
