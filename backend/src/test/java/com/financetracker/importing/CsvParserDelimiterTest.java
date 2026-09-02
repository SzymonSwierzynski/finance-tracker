package com.financetracker.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvParserDelimiterTest {

  @Test
  void picksSemicolonDespiteCommaHeavyPreamble() {
    String text =
        "Raport, wygenerowano, dnia\n"
            + "Data;Opis;Kwota\n"
            + "2026-08-01;Sklep;-45,99\n"
            + "2026-08-02;Kawa;-9,90\n";
    assertThat(CsvParser.parse(text, "").delimiter()).isEqualTo(';');
  }
}
