package com.financetracker.importing.detect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HeaderDetectorTest {

  private static List<String> r(String... cells) {
    return List.of(cells);
  }

  @Test
  void findsHeaderRowSkippingPreamble() {
    List<List<String>> rows =
        List.of(
            r("mBank S.A.", "Lista operacji", "", ""),
            r("#Klient", "JAN KOWALSKI", "", ""),
            r("#Data operacji", "#Opis operacji", "#Kwota", "#Saldo po operacji"),
            r("2026-08-02", "Biedronka", "-45,99", "9954,01"));
    assertThat(HeaderDetector.detect(rows)).isEqualTo(2);
  }

  @Test
  void returnsMinusOneWhenNoHeaderLike() {
    List<List<String>> rows =
        List.of(r("2026-08-02", "Biedronka", "-45,99"), r("2026-08-03", "Allegro", "-129,00"));
    assertThat(HeaderDetector.detect(rows)).isEqualTo(-1);
  }
}
