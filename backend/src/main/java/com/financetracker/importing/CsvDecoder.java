package com.financetracker.importing;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Byte-to-text decoding for CSV imports — a port of the prototype's {@code decodeBuffer}/{@code
 * looksMisdecoded}. Polish bank exports are frequently Windows-1250 (also ISO-8859-2); decoding is
 * non-fatal so an encoding mismatch surfaces as replacement characters rather than an exception.
 */
public final class CsvDecoder {

  /** Encodings offered in the import UI. */
  public static final List<String> SUPPORTED_ENCODINGS =
      List.of("utf-8", "windows-1250", "iso-8859-2");

  private CsvDecoder() {}

  /**
   * Decode raw bytes with the chosen encoding (defaults to UTF-8), replacing anything unmappable.
   */
  public static String decode(byte[] bytes, String encoding) {
    String name = (encoding == null || encoding.isBlank()) ? "utf-8" : encoding;
    return new String(bytes, Charset.forName(name));
  }

  /** A U+FFFD replacement char is a strong hint the encoding is wrong. */
  public static boolean looksMisdecoded(String text) {
    return text.indexOf('�') >= 0;
  }
}
