package com.financetracker.importing.detect;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;

class EncodingDetectorTest {

  @Test
  void detectsUtf8WhenClean() {
    byte[] utf8 = "Data;Kwota\n2026-08-01;-45,99".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(EncodingDetector.detect(utf8)).isEqualTo("utf-8");
  }

  @Test
  void detectsWindows1250WhenUtf8IsDirty() {
    byte[] cp1250 = "Opis\nŻabka;PŁATNOŚĆ".getBytes(Charset.forName("windows-1250"));
    assertThat(EncodingDetector.detect(cp1250)).isEqualTo("windows-1250");
  }
}
