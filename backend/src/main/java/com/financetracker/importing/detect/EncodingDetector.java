package com.financetracker.importing.detect;

import com.financetracker.importing.CsvDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Pick the text encoding for a CSV upload. UTF-8 when it decodes cleanly; otherwise Windows-1250 —
 * the Polish-bank default (ISO-8859-2 stays a manual fallback in the UI). Deterministic: a CP1250
 * file's single-byte ł/ż/ó are invalid UTF-8 multibyte sequences, so UTF-8 decoding yields U+FFFD.
 */
public final class EncodingDetector {

  private EncodingDetector() {}

  public static String detect(byte[] bytes) {
    String utf8 = new String(bytes, StandardCharsets.UTF_8);
    return CsvDecoder.looksMisdecoded(utf8) ? "windows-1250" : "utf-8";
  }
}
