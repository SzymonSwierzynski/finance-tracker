package com.financetracker.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 request fingerprints. Parts are length-prefixed so concatenations cannot collide. */
public final class Fingerprints {

  private Fingerprints() {}

  public static String sha256Hex(byte[]... parts) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      for (byte[] p : parts) {
        md.update(ByteBuffer.allocate(4).putInt(p.length).array());
        md.update(p);
      }
      return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** Fingerprint the JSON of {@code value} plus any extra byte parts (e.g. an uploaded file). */
  public static String of(ObjectMapper mapper, Object value, byte[]... extra) {
    try {
      byte[][] parts = new byte[extra.length + 1][];
      parts[0] = mapper.writeValueAsBytes(value);
      System.arraycopy(extra, 0, parts, 1, extra.length);
      return sha256Hex(parts);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not fingerprint request", e);
    }
  }
}
