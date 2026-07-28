package com.financetracker.common.idempotency;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Deterministic, collision-resistant request fingerprints. */
class FingerprintsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  record Sample(String a, int b) {}

  @Test
  void sameInputSameHex_differentInputDifferentHex() {
    String h1 = Fingerprints.sha256Hex("hello".getBytes(UTF_8));
    String h2 = Fingerprints.sha256Hex("hello".getBytes(UTF_8));
    String h3 = Fingerprints.sha256Hex("world".getBytes(UTF_8));
    assertThat(h1).isEqualTo(h2).hasSize(64); // SHA-256 hex
    assertThat(h1).isNotEqualTo(h3);
  }

  @Test
  void ofSerializesValueAndIsStable() {
    String a = Fingerprints.of(mapper, new Sample("x", 1));
    String b = Fingerprints.of(mapper, new Sample("x", 1));
    String c = Fingerprints.of(mapper, new Sample("x", 2));
    assertThat(a).isEqualTo(b);
    assertThat(a).isNotEqualTo(c);
  }

  @Test
  void extraPartsAreLengthDelimitedSoConcatenationCannotCollide() {
    String ab = Fingerprints.sha256Hex("ab".getBytes(UTF_8), "c".getBytes(UTF_8));
    String aBc = Fingerprints.sha256Hex("a".getBytes(UTF_8), "bc".getBytes(UTF_8));
    assertThat(ab).isNotEqualTo(aBc);
  }
}
