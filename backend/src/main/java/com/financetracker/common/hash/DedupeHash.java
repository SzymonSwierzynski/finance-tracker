package com.financetracker.common.hash;

import java.util.List;

/**
 * Small deterministic hash used to dedupe transactions (relied on by CSV import in Phase 4). Manual
 * entries compute one too, so the field is always populated and consistent across clients.
 *
 * <p>Faithful port of the prototype's {@code data/hash.ts}: FNV-1a, 32-bit, hex-encoded,
 * lower-case, zero-padded to 8 chars. Parts are stringified and joined with {@code '|'}. The
 * arithmetic mirrors JavaScript's {@code Math.imul} (32-bit signed multiply) — Java {@code int}
 * overflow wraps identically, and {@code %08x} on an {@code int} prints the unsigned 32-bit value,
 * matching {@code (h >>> 0).toString(16)}.
 */
public final class DedupeHash {

  private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
  private static final int FNV_PRIME = 0x01000193;

  private DedupeHash() {}

  /** Hash the {@code '|'}-joined string form of the given parts. */
  public static String of(List<Object> parts) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        sb.append('|');
      }
      sb.append(String.valueOf(parts.get(i)));
    }
    return hash(sb.toString());
  }

  private static String hash(String input) {
    int h = FNV_OFFSET_BASIS;
    for (int i = 0; i < input.length(); i++) {
      h ^= input.charAt(i);
      h *= FNV_PRIME; // 32-bit wraparound == Math.imul(h, prime)
    }
    return String.format("%08x", h);
  }
}
