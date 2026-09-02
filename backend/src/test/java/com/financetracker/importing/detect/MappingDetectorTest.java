package com.financetracker.importing.detect;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.importing.AmountMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class MappingDetectorTest {

  private static List<String> r(String... c) {
    return List.of(c);
  }

  private static final List<List<String>> ROWS =
      List.of(
          r(
              "#Data księgowania",
              "#Data operacji",
              "#Opis operacji",
              "#Tytuł",
              "#Nadawca/Odbiorca",
              "#Numer rachunku",
              "#Kwota",
              "#Saldo po operacji"),
          r(
              "2026-08-03",
              "2026-08-02",
              "PŁATNOŚĆ",
              "Biedronka",
              "BIEDRONKA",
              "11",
              "-45,99",
              "9954,01"),
          r("2026-08-04", "2026-08-03", "ZAKUP", "Allegro", "ALLEGRO", "22", "-129,00", "9825,01"),
          r(
              "2026-08-28",
              "2026-08-28",
              "PRZELEW",
              "Wynagrodzenie",
              "FIRMA",
              "88",
              "5 000,00",
              "13050,31"));

  @Test
  void detectsMbankColumns() {
    DetectedMapping m = MappingDetector.detect(ROWS, 0);
    assertThat(m.dateIndex()).isEqualTo(1); // operation date preferred over booking (idx 0)
    assertThat(m.dateFormat()).isEqualTo("yyyy-MM-dd");
    assertThat(m.amountMode()).isEqualTo(AmountMode.SIGNED);
    assertThat(m.amountIndex()).isEqualTo(6);
    assertThat(m.expenseIsNegative()).isTrue();
    assertThat(m.descriptionIndexes()).containsExactly(2, 3);
    assertThat(m.dateIndex()).isNotEqualTo(7);
    assertThat(m.amountIndex()).isNotEqualTo(7);
    assertThat(m.descriptionIndexes()).doesNotContain(7);
  }
}
