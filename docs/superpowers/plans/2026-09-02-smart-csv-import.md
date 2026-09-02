# Smart CSV Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect-first CSV import — pick account, drop file, confirm, done. Auto-detect encoding, the header row (skipping preamble/summary/footer), and the full column mapping (date / amount / description / mode / sign / format) via a PL+EN header-name dictionary, so the user never hand-builds a mapping or edits the file.

**Architecture:** A pure detection pipeline in `importing/detect/` (`EncodingDetector` → improved delimiter sniff → `HeaderDetector` → `MappingDetector`) feeds an **optional** mapping into the existing preview/commit path. When the `mapping` part is absent, `ImportService` detects, returns the mapping it used + income/expense sums + detection metadata. Frontend flips to detect-first with a plain-language banner and an "Adjust columns" fallback. New migration **V16** persists two extra profile fields.

**Tech Stack:** Spring Boot 3.5 / Java 21 / JPA / Apache Commons CSV (backend); React 19 + TS (frontend). Build with **Java 21** (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`), absolute paths.

**Spec:** `docs/superpowers/specs/2026-09-02-smart-csv-import-design.md`

---

## Standing rules for the executor (project §17)

- **Commit only when the user asks; push only when the user asks.** Pause before each `Commit` step.
- **Backend first, then frontend.** Keep both green. **Stop at the Phase A→B boundary** (after Task A8) for in-app testing.
- Reuse existing utilities: `MoneyUtil.parseAmountToMinor`, `CsvDateParser.parseFlexible`/`AUTO_DATE_FORMATS`, `CsvDecoder.looksMisdecoded`/`SUPPORTED_ENCODINGS`. Don't reimplement money/date parsing.
- Local-only docs (`HANDOFF.md`, `CLAUDE.md`) are git-ignored — update on disk, never commit.

---

## Task 0: Branch

- [ ] **Step 1: Branch off `main`**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
git checkout main && git switch -c backlog-smart-csv-import
```
Expected: `Switched to a new branch 'backlog-smart-csv-import'`.

---

# PHASE A — Backend detection

## Task A1: Synthesized fixture

**Files:**
- Create: `backend/src/test/resources/imports/mbank_sample.csv` (Windows-1250)

- [ ] **Step 1: Generate the fixture (UTF-8 source → CP1250)**

Run this exactly (it writes the UTF-8 form then transcodes to Windows-1250):

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
mkdir -p backend/src/test/resources/imports
cat > /tmp/mbank_utf8.csv <<'CSV'
mBank S.A.;Lista operacji;;;;;;
#Klient;JAN KOWALSKI;;;;;;
#Za okres:;2026-08-01;2026-08-31;;;;;
#Waluta;PLN;;;;;;
#Numer rachunku;12 3456 7890 0000 1111 2222 3333;;;;;;

#Data księgowania;#Data operacji;#Opis operacji;#Tytuł;#Nadawca/Odbiorca;#Numer rachunku;#Kwota;#Saldo po operacji
2026-08-03;2026-08-02;PŁATNOŚĆ KARTĄ;Biedronka Warszawa;BIEDRONKA;11 1111;-45,99;9954,01
2026-08-04;2026-08-03;ZAKUP INTERNET;Allegro;ALLEGRO SP;22 2222;-129,00;9825,01
2026-08-05;2026-08-05;PŁATNOŚĆ KARTĄ;Żabka;ZABKA;33 3333;-12,50;9812,51
2026-08-07;2026-08-06;PALIWO;Orlen;PKN ORLEN;44 4444;-210,30;9602,21
2026-08-10;2026-08-10;SUBSKRYPCJA;Netflix;NETFLIX;55 5555;-43,00;9559,21
2026-08-12;2026-08-11;OPŁATA;Poczta Polska;POCZTA;66 6666;-8,90;9550,31
2026-08-15;2026-08-15;PRZELEW;Czynsz mieszkanie;WSPÓLNOTA;77 7777;-1 500,00;8050,31
2026-08-28;2026-08-28;PRZELEW PRZYCH.;Wynagrodzenie;PRACODAWCA;88 8888;5 000,00;13050,31
2026-08-29;2026-08-29;ZWROT;Zwrot Allegro;ALLEGRO SP;22 2222;25,00;13075,31
2026-08-30;2026-08-30;PRZELEW PRZYCH.;Od Anna;ANNA NOWAK;99 9999;150,00;13225,31

#Podsumowanie;;;;;;;
#Liczba operacji;10;;;;;;
#Suma obciążeń;-1 949,69;;;;;;
#Suma uznań;5 175,00;;;;;;
CSV
iconv -f UTF-8 -t WINDOWS-1250 /tmp/mbank_utf8.csv > backend/src/test/resources/imports/mbank_sample.csv
```

- [ ] **Step 2: Verify it is genuinely CP1250 (UTF-8 decode is dirty)**

```bash
python3 - <<'PY'
raw = open('backend/src/test/resources/imports/mbank_sample.csv','rb').read()
print('utf8_dirty=', '�' in raw.decode('utf-8', 'replace'))
print('cp1250_clean=', '�' not in raw.decode('cp1250'))
print('has_Zabka=', 'Żabka' in raw.decode('cp1250'))
PY
```
Expected: `utf8_dirty= True`, `cp1250_clean= True`, `has_Zabka= True`.

---

## Task A2: EncodingDetector

**Files:**
- Create: `backend/src/main/java/com/financetracker/importing/detect/EncodingDetector.java`
- Test: `backend/src/test/java/com/financetracker/importing/detect/EncodingDetectorTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run it — expect FAIL (class does not exist)**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.importing.detect.EncodingDetectorTest'
```
Expected: compile/assertion failure.

- [ ] **Step 3: Implement**

```java
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
```

- [ ] **Step 4: Run it — expect PASS**

Same command as Step 2. Expected: PASS (2 tests).

---

## Task A3: Robust delimiter sniff

**Files:**
- Modify: `backend/src/main/java/com/financetracker/importing/CsvParser.java:44-57` (`detectDelimiter`)
- Test: `backend/src/test/java/com/financetracker/importing/CsvParserDelimiterTest.java`

- [ ] **Step 1: Write the failing test** — a preamble whose first line has commas must not fool the sniff

```java
package com.financetracker.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvParserDelimiterTest {

  @Test
  void picksSemicolonDespiteCommaHeavyPreamble() {
    // First line is comma-heavy prose; the real, consistent delimiter across the file is ';'.
    String text =
        "Raport, wygenerowano, dnia\n"
            + "Data;Opis;Kwota\n"
            + "2026-08-01;Sklep;-45,99\n"
            + "2026-08-02;Kawa;-9,90\n";
    assertThat(CsvParser.parse(text, "").delimiter()).isEqualTo(';');
  }
}
```

- [ ] **Step 2: Run — expect FAIL** (`,` picked from the first line today)

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.importing.CsvParserDelimiterTest'
```

- [ ] **Step 3: Replace `detectDelimiter` (and drop the now-unused single-line helper)**

Replace the body of `detectDelimiter` and `firstNonEmptyLine` (lines 44-66) with:

```java
  /**
   * Choose the delimiter that partitions the file most consistently. Score each candidate across the
   * first 40 non-empty lines by how many lines share the modal occurrence-count (consistency),
   * tie-broken by the modal count itself. Robust against a comma-heavy preamble before a ';' table.
   */
  private static char detectDelimiter(String text) {
    String[] lines = text.split("\r\n|\r|\n");
    char best = ',';
    int bestConsistency = -1;
    int bestModal = -1;
    for (char candidate : CANDIDATE_DELIMITERS) {
      java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
      int seen = 0;
      for (String line : lines) {
        if (line.isBlank()) {
          continue;
        }
        counts.merge(countOccurrences(line, candidate), 1, Integer::sum);
        if (++seen >= 40) {
          break;
        }
      }
      int modal = 0;
      int consistency = 0;
      for (var e : counts.entrySet()) {
        if (e.getKey() > 0 && e.getValue() > consistency) {
          consistency = e.getValue();
          modal = e.getKey();
        }
      }
      if (consistency > bestConsistency
          || (consistency == bestConsistency && modal > bestModal)) {
        bestConsistency = consistency;
        bestModal = modal;
        best = candidate;
      }
    }
    return best;
  }
```

(`countOccurrences` stays; `firstNonEmptyLine` is removed.)

- [ ] **Step 4: Run — expect PASS**, then the existing importer tests still pass:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.importing.*'
```
Expected: PASS (new test + all existing import tests green).

---

## Task A4: HeaderDetector + dictionary

**Files:**
- Create: `backend/src/main/java/com/financetracker/importing/detect/HeaderDictionary.java`
- Create: `backend/src/main/java/com/financetracker/importing/detect/HeaderDetector.java`
- Test: `backend/src/test/java/com/financetracker/importing/detect/HeaderDetectorTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.importing.detect.HeaderDetectorTest'
```

- [ ] **Step 3: Implement the dictionary**

```java
package com.financetracker.importing.detect;

import java.util.List;
import java.util.Locale;

/**
 * PL + EN header-name vocabulary for column-role detection — the single tuning surface for new banks
 * (no per-bank presets). Cells are normalized before matching: leading '#'/quotes stripped, Polish
 * lower-cased, whitespace collapsed.
 */
public final class HeaderDictionary {

  private static final Locale PL = Locale.forLanguageTag("pl");

  public static final List<String> DATE =
      List.of("data operacji", "data transakcji", "data waluty", "data księgowania", "data", "date");
  public static final List<String> AMOUNT = List.of("kwota operacji", "kwota", "amount");
  public static final List<String> DEBIT = List.of("kwota obciążenia", "obciążenia", "wypłata", "debit");
  public static final List<String> CREDIT = List.of("kwota uznania", "uznania", "wpłata", "credit");
  public static final List<String> DESCRIPTION =
      List.of(
          "opis operacji", "opis", "tytuł operacji", "tytuł", "nadawca/odbiorca", "nadawca",
          "odbiorca", "kontrahent", "szczegóły", "description", "details", "title");
  public static final List<String> BALANCE = List.of("saldo po operacji", "saldo", "balance");
  public static final List<String> ACCOUNT =
      List.of("numer rachunku", "numer konta", "rachunek", "konto", "account");

  private HeaderDictionary() {}

  /** Normalize a raw header cell for dictionary matching. */
  public static String normalize(String cell) {
    if (cell == null) {
      return "";
    }
    String s = cell.trim();
    while (s.startsWith("#") || s.startsWith("\"") || s.startsWith("'")) {
      s = s.substring(1).trim();
    }
    if (s.endsWith("\"") || s.endsWith("'")) {
      s = s.substring(0, s.length() - 1).trim();
    }
    return s.toLowerCase(PL).replaceAll("\\s+", " ").trim();
  }

  /** True when the normalized cell equals, or contains as a phrase, any token in {@code vocab}. */
  public static boolean matches(String normalized, List<String> vocab) {
    for (String token : vocab) {
      if (normalized.equals(token) || normalized.contains(token)) {
        return true;
      }
    }
    return false;
  }

  /** How many of the six role vocabularies this cell hits (for header scoring). */
  static boolean isAnyRole(String normalized) {
    return matches(normalized, DATE)
        || matches(normalized, AMOUNT)
        || matches(normalized, DEBIT)
        || matches(normalized, CREDIT)
        || matches(normalized, DESCRIPTION)
        || matches(normalized, BALANCE)
        || matches(normalized, ACCOUNT);
  }
}
```

- [ ] **Step 4: Implement the detector**

```java
package com.financetracker.importing.detect;

import java.util.List;

/**
 * Locate the header row in parsed CSV rows, skipping any preamble/summary. Scans the first 50 rows
 * and scores each by how many cells hit the {@link HeaderDictionary} role vocabularies; the top row
 * wins when it scores at least {@value #MIN_SCORE}. Returns the 0-based parsed row index, or -1 when
 * nothing looks like a header (caller falls back to the hasHeader heuristic).
 */
public final class HeaderDetector {

  private static final int SCAN_LIMIT = 50;
  private static final int MIN_SCORE = 3;

  private HeaderDetector() {}

  public static int detect(List<List<String>> rows) {
    int bestIndex = -1;
    int bestScore = MIN_SCORE - 1;
    int limit = Math.min(rows.size(), SCAN_LIMIT);
    for (int i = 0; i < limit; i++) {
      int score = score(rows.get(i));
      if (score > bestScore) {
        bestScore = score;
        bestIndex = i;
      }
    }
    return bestIndex;
  }

  private static int score(List<String> row) {
    int hits = 0;
    for (String cell : row) {
      String n = HeaderDictionary.normalize(cell);
      if (!n.isEmpty() && HeaderDictionary.isAnyRole(n)) {
        hits++;
      }
    }
    return hits;
  }
}
```

- [ ] **Step 5: Run — expect PASS** (2 tests).

---

## Task A5: MappingDetector

**Files:**
- Create: `backend/src/main/java/com/financetracker/importing/detect/DetectedMapping.java`
- Create: `backend/src/main/java/com/financetracker/importing/detect/MappingDetector.java`
- Test: `backend/src/test/java/com/financetracker/importing/detect/MappingDetectorTest.java`

- [ ] **Step 1: Write the failing test** (drives the exact spec acceptance for column roles)

```java
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
          r("#Data księgowania", "#Data operacji", "#Opis operacji", "#Tytuł",
              "#Nadawca/Odbiorca", "#Numer rachunku", "#Kwota", "#Saldo po operacji"),
          r("2026-08-03", "2026-08-02", "PŁATNOŚĆ", "Biedronka", "BIEDRONKA", "11", "-45,99", "9954,01"),
          r("2026-08-04", "2026-08-03", "ZAKUP", "Allegro", "ALLEGRO", "22", "-129,00", "9825,01"),
          r("2026-08-28", "2026-08-28", "PRZELEW", "Wynagrodzenie", "FIRMA", "88", "5 000,00", "13050,31"));

  @Test
  void detectsMbankColumns() {
    DetectedMapping m = MappingDetector.detect(ROWS, 0);
    assertThat(m.dateIndex()).isEqualTo(1); // operation date preferred over booking (idx 0)
    assertThat(m.dateFormat()).isEqualTo("yyyy-MM-dd");
    assertThat(m.amountMode()).isEqualTo(AmountMode.SIGNED);
    assertThat(m.amountIndex()).isEqualTo(6);
    assertThat(m.expenseIsNegative()).isTrue();
    assertThat(m.descriptionIndexes()).containsExactly(2, 3);
    // Saldo (idx 7) is never chosen for any role:
    assertThat(m.dateIndex()).isNotEqualTo(7);
    assertThat(m.amountIndex()).isNotEqualTo(7);
    assertThat(m.descriptionIndexes()).doesNotContain(7);
  }
}
```

- [ ] **Step 2: Run — expect FAIL.**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.importing.detect.MappingDetectorTest'
```

- [ ] **Step 3: Implement the result record**

```java
package com.financetracker.importing.detect;

import com.financetracker.importing.AmountMode;
import java.util.List;
import java.util.Map;

/**
 * The outcome of column-role detection for one header row. {@code descriptionIndexes} may hold
 * several columns (joined by a space at build time). {@code recognizedColumns} maps each resolved
 * role name to its raw header label, for the UI banner. Unused indexes are -1.
 */
public record DetectedMapping(
    int dateIndex,
    String dateFormat,
    AmountMode amountMode,
    int amountIndex,
    boolean expenseIsNegative,
    int debitIndex,
    int creditIndex,
    List<Integer> descriptionIndexes,
    Map<String, String> recognizedColumns) {}
```

- [ ] **Step 4: Implement the detector**

```java
package com.financetracker.importing.detect;

import com.financetracker.importing.AmountMode;
import com.financetracker.importing.CsvDateParser;
import com.financetracker.common.money.MoneyUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assign column roles from a header row plus a data sample. Date prefers the operation date and is
 * confirmed by value-sniffing (which also picks the date format); amount excludes any balance column
 * (the Saldo guard) and infers signed-vs-debit/credit and the expense sign; description collects every
 * text-role column in header order. Pure and deterministic.
 */
public final class MappingDetector {

  private static final int SAMPLE = 20;

  private MappingDetector() {}

  public static DetectedMapping detect(List<List<String>> rows, int headerRowIndex) {
    List<String> header = rows.get(headerRowIndex);
    int cols = header.size();
    String[] norm = new String[cols];
    for (int c = 0; c < cols; c++) {
      norm[c] = HeaderDictionary.normalize(header.get(c));
    }
    List<List<String>> sample = new ArrayList<>();
    for (int i = headerRowIndex + 1; i < rows.size() && sample.size() < SAMPLE; i++) {
      sample.add(rows.get(i));
    }
    Map<String, String> recognized = new LinkedHashMap<>();

    // --- Date: dictionary preference, confirmed + format chosen by value-sniff ---
    int dateIndex = -1;
    int datePref = Integer.MAX_VALUE; // lower = more preferred
    for (int c = 0; c < cols; c++) {
      if (isBalanceOrAccount(norm[c]) || !HeaderDictionary.matches(norm[c], HeaderDictionary.DATE)) {
        continue;
      }
      int pref = HeaderDictionary.DATE.indexOf(firstToken(norm[c], HeaderDictionary.DATE));
      if (pref < datePref) {
        datePref = pref;
        dateIndex = c;
      }
    }
    String dateFormat = sniffDateFormat(sample, dateIndex);
    if (dateIndex >= 0) {
      recognized.put("date", header.get(dateIndex));
    }

    // --- Amount: signed vs debit/credit, Saldo guarded ---
    int amountIndex = -1;
    int debitIndex = -1;
    int creditIndex = -1;
    AmountMode mode = AmountMode.SIGNED;
    for (int c = 0; c < cols; c++) {
      if (isBalanceOrAccount(norm[c])) {
        continue; // Saldo / account never an amount
      }
      if (debitIndex < 0 && HeaderDictionary.matches(norm[c], HeaderDictionary.DEBIT)) {
        debitIndex = c;
      } else if (creditIndex < 0 && HeaderDictionary.matches(norm[c], HeaderDictionary.CREDIT)) {
        creditIndex = c;
      } else if (amountIndex < 0 && HeaderDictionary.matches(norm[c], HeaderDictionary.AMOUNT)) {
        amountIndex = c;
      }
    }
    boolean expenseIsNegative = true;
    if (debitIndex >= 0 && creditIndex >= 0) {
      mode = AmountMode.DEBIT_CREDIT;
      amountIndex = -1;
      recognized.put("debit", header.get(debitIndex));
      recognized.put("credit", header.get(creditIndex));
    } else if (amountIndex >= 0) {
      mode = AmountMode.SIGNED;
      debitIndex = -1;
      creditIndex = -1;
      expenseIsNegative = sampleHasNegative(sample, amountIndex);
      recognized.put("amount", header.get(amountIndex));
    }

    // --- Description: every text-role column, in header order ---
    List<Integer> descriptionIndexes = new ArrayList<>();
    for (int c = 0; c < cols; c++) {
      if (isBalanceOrAccount(norm[c]) || c == dateIndex || c == amountIndex) {
        continue;
      }
      if (HeaderDictionary.matches(norm[c], HeaderDictionary.DESCRIPTION)) {
        descriptionIndexes.add(c);
      }
    }
    if (!descriptionIndexes.isEmpty()) {
      StringBuilder label = new StringBuilder();
      for (int idx : descriptionIndexes) {
        label.append(label.isEmpty() ? "" : " + ").append(header.get(idx));
      }
      recognized.put("description", label.toString());
    }

    return new DetectedMapping(
        dateIndex, dateFormat, mode, amountIndex, expenseIsNegative,
        debitIndex, creditIndex, descriptionIndexes, recognized);
  }

  private static boolean isBalanceOrAccount(String n) {
    return HeaderDictionary.matches(n, HeaderDictionary.BALANCE)
        || HeaderDictionary.matches(n, HeaderDictionary.ACCOUNT);
  }

  /** The dictionary token (from {@code vocab}) that {@code n} matched, for preference ranking. */
  private static String firstToken(String n, List<String> vocab) {
    for (String token : vocab) {
      if (n.equals(token) || n.contains(token)) {
        return token;
      }
    }
    return n;
  }

  /** Pick the AUTO format that parses the most sample cells in {@code col}; default yyyy-MM-dd. */
  private static String sniffDateFormat(List<List<String>> sample, int col) {
    if (col < 0) {
      return "auto";
    }
    String best = "yyyy-MM-dd";
    int bestHits = -1;
    for (String fmt : CsvDateParser.AUTO_DATE_FORMATS) {
      int hits = 0;
      for (List<String> row : sample) {
        if (col < row.size() && CsvDateParser.parseFlexible(row.get(col), fmt) != null) {
          hits++;
        }
      }
      if (hits > bestHits) {
        bestHits = hits;
        best = fmt;
      }
    }
    return best;
  }

  private static boolean sampleHasNegative(List<List<String>> sample, int col) {
    for (List<String> row : sample) {
      if (col < row.size()) {
        Long v = MoneyUtil.parseAmountToMinor(row.get(col));
        if (v != null && v < 0) {
          return true;
        }
      }
    }
    return true; // Polish exports use negative-for-outflow; default to it.
  }
}
```

- [ ] **Step 5: Run — expect PASS** (the mBank column assertions).

---

## Task A6: DTO + entity + migration V16

**Files:**
- Modify: `backend/src/main/java/com/financetracker/importing/dto/ImportMapping.java`
- Modify: `backend/src/main/java/com/financetracker/importing/dto/PreviewResponse.java`
- Create: `backend/src/main/java/com/financetracker/importing/dto/DetectionInfo.java`
- Modify: `backend/src/main/java/com/financetracker/importing/ImportProfile.java`
- Create: `backend/src/main/resources/db/migration/V16__import_profile_detection.sql`
- Modify: `backend/src/main/java/com/financetracker/importing/ImportRowBuilder.java`

- [ ] **Step 1: `ImportMapping` — add optional detection fields**

Replace the record with (keeps every existing field; adds two nullable ones):

```java
package com.financetracker.importing.dto;

import com.financetracker.importing.AmountMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * How to interpret a CSV against its columns — remembered per account. {@code delimiter}/{@code
 * encoding} may be blank to auto-detect; {@code dateFormat} may be {@code "auto"}; column indexes are
 * 0-based, an unused index is {@code -1}. {@code headerRowIndex} (null = derive from {@code hasHeader})
 * skips a mid-file preamble; {@code descriptionIndexes} (null/empty = use {@code descriptionIndex})
 * joins several columns into the description.
 */
public record ImportMapping(
    @Size(max = 4) String delimiter,
    @Size(max = 40) String encoding,
    boolean hasHeader,
    Integer headerRowIndex,
    int dateIndex,
    @Size(max = 40) String dateFormat,
    int descriptionIndex,
    List<Integer> descriptionIndexes,
    @NotNull AmountMode amountMode,
    int amountIndex,
    boolean expenseIsNegative,
    int debitIndex,
    int creditIndex) {}
```

> **Note for the executor:** this adds two constructor args. Every `new ImportMapping(...)` call site must be updated — they are: `ImportService.toMapping` (Task A7), `ImportPage.tsx` default (Phase B), and any test builders. The compiler will list them; fix each.

- [ ] **Step 2: `DetectionInfo` DTO**

```java
package com.financetracker.importing.dto;

import java.util.Map;

/**
 * What auto-detection concluded, for the UI banner. {@code recognizedColumns} maps a role
 * ("date"/"amount"/"description"/…) to the raw header label it was read from.
 */
public record DetectionInfo(
    String encoding, Integer headerRowIndex, Map<String, String> recognizedColumns) {}
```

- [ ] **Step 3: `PreviewResponse` — expose the used mapping, detection, and sums**

```java
package com.financetracker.importing.dto;

import java.util.List;

/** CSV preview result (see fields). {@code mapping} is what was used — detected or supplied. */
public record PreviewResponse(
    String delimiter,
    boolean misdecoded,
    int totalRows,
    int validRows,
    int duplicateRows,
    long incomeMinor,
    long expenseMinor,
    ImportMapping mapping,
    DetectionInfo detection,
    List<PreviewRow> rows) {}
```

- [ ] **Step 4: `ImportProfile` entity — two nullable columns**

Add after `creditIndex` (line 51):

```java
  @Column(name = "header_row_index")
  private Integer headerRowIndex;

  @Column(name = "description_indexes")
  private String descriptionIndexes; // comma-joined, e.g. "2,3"; null = use descriptionIndex
```

- [ ] **Step 5: Migration `V16__import_profile_detection.sql`**

```sql
-- Smart CSV import: remember the detected header row and multi-column description per profile.
-- Forward-only; existing profiles keep working (NULL header_row_index -> hasHeader; NULL
-- description_indexes -> single description_index).
ALTER TABLE import_profiles
    ADD COLUMN header_row_index  INTEGER,
    ADD COLUMN description_indexes TEXT;
```

- [ ] **Step 6: `ImportRowBuilder` — start row + multi-column description**

Replace lines 20-28 (the `build` head through the `description`/`date` reads) with:

```java
  public static List<ParsedImportRow> build(List<List<String>> rows, ImportMapping mapping) {
    List<ParsedImportRow> out = new ArrayList<>();
    int startAt =
        mapping.headerRowIndex() != null
            ? mapping.headerRowIndex() + 1
            : (mapping.hasHeader() ? 1 : 0);

    for (int i = startAt; i < rows.size(); i++) {
      List<String> raw = rows.get(i);
      String description = description(raw, mapping);
      LocalDate date =
          CsvDateParser.parseFlexible(cell(raw, mapping.dateIndex()), mapping.dateFormat());
```

Then add this helper next to `cell(...)`:

```java
  /** Join the mapped description columns with a space; fall back to the single index. */
  private static String description(List<String> row, ImportMapping mapping) {
    List<Integer> idxs = mapping.descriptionIndexes();
    if (idxs == null || idxs.isEmpty()) {
      return cell(row, mapping.descriptionIndex());
    }
    StringBuilder sb = new StringBuilder();
    for (int idx : idxs) {
      String c = cell(row, idx);
      if (!c.isEmpty()) {
        sb.append(sb.length() == 0 ? "" : " ").append(c);
      }
    }
    return sb.toString();
  }
```

- [ ] **Step 7: Compile check**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```
Expected: fails only where `new ImportMapping(...)` / `new PreviewResponse(...)` need the new args — fixed in Task A7. (Do A7 before re-running.)

---

## Task A7: Wire detection into ImportService + Controller

**Files:**
- Modify: `backend/src/main/java/com/financetracker/importing/ImportService.java`
- Modify: `backend/src/main/java/com/financetracker/importing/ImportController.java`
- Create: `backend/src/main/java/com/financetracker/importing/detect/CsvMappingDetector.java` (façade tying the stages together)

- [ ] **Step 1: Detection façade** — one call that produces a full `ImportMapping` from bytes

```java
package com.financetracker.importing.detect;

import com.financetracker.importing.CsvParser;
import com.financetracker.importing.CsvParser.ParsedCsv;
import com.financetracker.importing.CsvDecoder;
import com.financetracker.importing.dto.DetectionInfo;
import com.financetracker.importing.dto.ImportMapping;
import java.util.List;

/**
 * End-to-end auto-detection: bytes → encoding → parsed rows → header row → column roles → a complete
 * {@link ImportMapping} plus the {@link DetectionInfo} for the UI. Falls back gracefully (no header
 * found → treat row 0 as header) so preview always returns something.
 */
public final class CsvMappingDetector {

  public record Detected(ImportMapping mapping, DetectionInfo info) {}

  private CsvMappingDetector() {}

  public static Detected detect(byte[] file) {
    String encoding = EncodingDetector.detect(file);
    String text = CsvDecoder.decode(file, encoding);
    ParsedCsv parsed = CsvParser.parse(text, "");
    List<List<String>> rows = parsed.rows();

    int headerRow = HeaderDetector.detect(rows);
    boolean hasHeader = headerRow >= 0;
    int effectiveHeader = hasHeader ? headerRow : 0;
    DetectedMapping cols =
        rows.isEmpty()
            ? new DetectedMapping(0, "auto", com.financetracker.importing.AmountMode.SIGNED, 2, true,
                -1, -1, List.of(1), java.util.Map.of())
            : MappingDetector.detect(rows, effectiveHeader);

    ImportMapping mapping =
        new ImportMapping(
            String.valueOf(parsed.delimiter()),
            encoding,
            hasHeader,
            hasHeader ? headerRow : null,
            cols.dateIndex(),
            cols.dateFormat(),
            cols.descriptionIndexes().isEmpty() ? 1 : cols.descriptionIndexes().get(0),
            cols.descriptionIndexes(),
            cols.amountMode(),
            cols.amountIndex(),
            cols.expenseIsNegative(),
            cols.debitIndex(),
            cols.creditIndex());
    return new Detected(mapping, new DetectionInfo(encoding, hasHeader ? headerRow : null, cols.recognizedColumns()));
  }
}
```

- [ ] **Step 2: `ImportService.preview` — detect when mapping is null, add sums**

Change the `preview` signature to accept a nullable mapping and detect. Replace the method (lines 82-127) with:

```java
  @Transactional(readOnly = true)
  public PreviewResponse preview(long userId, long accountId, byte[] file, ImportMapping mapping) {
    Account account = requireOwnedAccount(userId, accountId);
    DetectionInfo detection = null;
    if (mapping == null) {
      // Prefer a remembered profile; else auto-detect from the file.
      var saved = importProfileRepository.findByUserIdAndAccountId(userId, accountId);
      if (saved.isPresent()) {
        mapping = toMapping(saved.get());
      } else {
        CsvMappingDetector.Detected d = CsvMappingDetector.detect(file);
        mapping = d.mapping();
        detection = d.info();
      }
    }
    String text = CsvDecoder.decode(file, mapping.encoding());
    boolean misdecoded = CsvDecoder.looksMisdecoded(text);
    ParsedCsv parsed = CsvParser.parse(text, mapping.delimiter());
    List<ParsedImportRow> rows =
        requireWithinRowLimit(ImportRowBuilder.build(parsed.rows(), mapping));

    Set<String> existing =
        new HashSet<>(transactionRepository.findDedupeHashesByUserIdAndAccountId(userId, accountId));
    Set<String> seenInFile = new HashSet<>();

    List<PreviewRow> previewRows = new ArrayList<>();
    int validRows = 0;
    int duplicateRows = 0;
    long incomeMinor = 0;
    long expenseMinor = 0;
    for (ParsedImportRow row : rows) {
      boolean duplicate = false;
      if (row.valid()) {
        validRows++;
        if (row.type() == TransactionType.INCOME) {
          incomeMinor += row.amountMinor();
        } else {
          expenseMinor += row.amountMinor();
        }
        String hash = dedupeHash(row, account.getCurrency(), accountId);
        duplicate = existing.contains(hash) || !seenInFile.add(hash);
        if (duplicate) {
          duplicateRows++;
        }
      }
      previewRows.add(
          new PreviewRow(row.index(), row.date(), row.amountMinor(), row.type(),
              row.description(), row.valid(), row.error(), duplicate));
    }
    return new PreviewResponse(
        String.valueOf(parsed.delimiter()), misdecoded, rows.size(), validRows, duplicateRows,
        incomeMinor, expenseMinor, mapping, detection, previewRows);
  }
```

Add imports: `com.financetracker.importing.detect.CsvMappingDetector`, `com.financetracker.importing.dto.DetectionInfo`. (`TransactionType` is already imported.)

- [ ] **Step 3: `ImportService.commit` — detect when mapping is null**

At the top of `commit(...)` (before building the fingerprint, line 137), add:

```java
    if (mapping == null) {
      mapping =
          importProfileRepository
              .findByUserIdAndAccountId(userId, accountId)
              .map(ImportService::toMapping)
              .orElseGet(() -> CsvMappingDetector.detect(file).mapping());
    }
```

- [ ] **Step 4: `toMapping` + `upsertProfile` — round-trip the two new fields**

Replace `toMapping` (lines 333-346):

```java
  private static ImportMapping toMapping(ImportProfile p) {
    List<Integer> descIdx =
        (p.getDescriptionIndexes() == null || p.getDescriptionIndexes().isBlank())
            ? null
            : java.util.Arrays.stream(p.getDescriptionIndexes().split(","))
                .map(String::trim)
                .map(Integer::valueOf)
                .toList();
    return new ImportMapping(
        p.getDelimiter(), p.getEncoding(), p.isHasHeader(), p.getHeaderRowIndex(),
        p.getDateIndex(), p.getDateFormat(), p.getDescriptionIndex(), descIdx,
        p.getAmountMode(), p.getAmountIndex(), p.isExpenseIsNegative(),
        p.getDebitIndex(), p.getCreditIndex());
  }
```

In `upsertProfile` (after `profile.setCreditIndex(...)`, line 301) add:

```java
    profile.setHeaderRowIndex(mapping.headerRowIndex());
    profile.setDescriptionIndexes(
        mapping.descriptionIndexes() == null || mapping.descriptionIndexes().isEmpty()
            ? null
            : mapping.descriptionIndexes().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));
```

- [ ] **Step 5: `ImportController` — make the mapping part optional**

In both `preview` and `commit`, change the mapping part to optional:

```java
      @RequestPart(value = "mapping", required = false) @Valid ImportMapping mapping) {
```

- [ ] **Step 6: Full compile**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

---

## Task A8: End-to-end detection test + full gate + commit

**Files:**
- Test: `backend/src/test/java/com/financetracker/importing/ImportSmartDetectionTest.java`

- [ ] **Step 1: Write the integration test** (drives the fixture through the real preview/commit endpoints, asserting the §7 oracle)

```java
package com.financetracker.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Smart CSV import: the mBank-style fixture auto-detects and commits with correct signs/sums. */
class ImportSmartDetectionTest extends AbstractIntegrationTest {

  private byte[] fixture() throws Exception {
    return Files.readAllBytes(Path.of("src/test/resources/imports/mbank_sample.csv"));
  }

  private MockMultipartFile file() throws Exception {
    return new MockMultipartFile("file", "mbank_sample.csv", "text/csv", fixture());
  }

  @Test
  void previewAutoDetectsMappingAndSums() throws Exception {
    RegisteredUser user = register("smartcsv-p@example.com", "password123");
    long account = createChecking(user);

    mockMvc
        .perform(
            multipart("/api/v1/imports/preview")
                .file(file())
                .param("accountId", String.valueOf(account))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validRows").value(10))
        .andExpect(jsonPath("$.duplicateRows").value(0))
        .andExpect(jsonPath("$.incomeMinor").value(517500))
        .andExpect(jsonPath("$.expenseMinor").value(194969))
        .andExpect(jsonPath("$.detection.encoding").value("windows-1250"))
        .andExpect(jsonPath("$.detection.headerRowIndex").value(5))
        .andExpect(jsonPath("$.mapping.dateIndex").value(1))
        .andExpect(jsonPath("$.mapping.amountIndex").value(6))
        .andExpect(jsonPath("$.mapping.descriptionIndexes[0]").value(2))
        .andExpect(jsonPath("$.mapping.descriptionIndexes[1]").value(3));
  }

  @Test
  void commitAutoDetectsAndInsertsWithCorrectSigns() throws Exception {
    RegisteredUser user = register("smartcsv-c@example.com", "password123");
    long account = createChecking(user);

    mockMvc
        .perform(
            multipart("/api/v1/imports/commit")
                .file(file())
                .param("accountId", String.valueOf(account))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(10));

    // 7 expense + 3 income landed on the account.
    var json =
        objectMapper.readTree(
            mockMvc
                .perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/transactions?size=50&accountId=" + account)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long income =
        java.util.stream.StreamSupport.stream(json.get("items").spliterator(), false)
            .filter(n -> n.get("type").asText().equals("income"))
            .count();
    assertThat(income).isEqualTo(3);
    assertThat(json.get("total").asInt()).isEqualTo(10);
  }

  @Test
  void previewIsScopedPerUser() throws Exception {
    RegisteredUser alice = register("smartcsv-a@example.com", "password123");
    RegisteredUser bob = register("smartcsv-b@example.com", "password123");
    long acct = createChecking(alice);
    mockMvc
        .perform(
            multipart("/api/v1/imports/preview")
                .file(file())
                .param("accountId", String.valueOf(acct))
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
        .andExpect(status().isNotFound());
  }

  private long createChecking(RegisteredUser user) throws Exception {
    var result =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Checking\",\"type\":\"checking\",\"currency\":\"PLN\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
```

> **Executor check:** if `AbstractIntegrationTest` exposes different helper names (`register`, `bearer`, `mockMvc`, `objectMapper`), match them — mirror `BulkTransactionOpsTest`. The multipart preview/commit send **no `mapping` part**, exercising auto-detection.

- [ ] **Step 2: Targeted test, then the full gate**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.importing.*'
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessApply build
```
Expected: import tests PASS; `build` BUILD SUCCESSFUL (Spotless + all tests + Flyway validate + JaCoCo ≥ 0.85). If coverage dips, the detectors are new non-boilerplate — the unit + integration tests above should cover them; add a debit/credit-mode `MappingDetector` case if the gate flags an uncovered branch.

- [ ] **Step 3: Commit** (after the user's go-ahead)

```bash
git add backend/src/main/java/com/financetracker/importing/ \
        backend/src/main/resources/db/migration/V16__import_profile_detection.sql \
        backend/src/test/java/com/financetracker/importing/ \
        backend/src/test/resources/imports/mbank_sample.csv
git commit -m "feat(backend): smart CSV import — encoding/header/column auto-detection (V16)"
```

- [ ] **Step 4: STOP at the phase boundary** — report green, let the user test preview auto-detection in-app before Phase B.

---

# PHASE B — Frontend detect-first

## Task B1: Regenerate API types

- [ ] **Step 1: With the backend running, regenerate the OpenAPI types**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/frontend
npm run gen:api
```
Then update the curated `ImportMapping`/`PreviewResponse` aliases in `src/api/types.ts` to include `headerRowIndex`, `descriptionIndexes`, `incomeMinor`, `expenseMinor`, `mapping`, and a `DetectionInfo` type. Expected: `npx tsc --noEmit` clean.

## Task B2: Detect-first ImportPage

**Files:**
- Modify: `frontend/src/features/import/ImportPage.tsx`
- Modify: `frontend/src/features/import/hooks.ts` + `api.ts` (allow a null mapping)
- Modify: `frontend/src/lib/i18n.ts`

- [ ] **Step 1: Allow preview/commit without a mapping** — in `api.ts`, make `form()` omit the `mapping` part when `mapping` is `null`, and widen the `preview`/`commit` mapping param to `ImportMapping | null`. In `hooks.ts`, widen `ImportArgs.mapping` to `ImportMapping | null`.

- [ ] **Step 2: Flip the flow** — on *account + file* selected, immediately call `preview` with `mapping: null`. Seed the local `mapping` state from `previewData.mapping` (the detected one) so "Adjust columns" starts from the detection. Drop the mandatory manual step 2; keep it behind an expander.

- [ ] **Step 3: Detection banner + sanity totals** — above the preview table, render a banner from `previewData.detection` and the sums, e.g.:

```tsx
{previewData.detection && (
  <div className="rounded-lg border border-border bg-surface-2 px-4 py-2.5 text-sm">
    <span className="font-medium">{t('import.detected')}:</span>{' '}
    {previewData.detection.encoding} · {t('import.headerRow', { row: (previewData.detection.headerRowIndex ?? 0) + 1 })}
    {Object.entries(previewData.detection.recognizedColumns).map(([role, label]) => (
      <span key={role} className="ml-2 text-fg-soft">· {t(`import.role_${role}`)}: {label}</span>
    ))}
    <div className="mt-1 text-fg-soft">
      {t('import.income')}: <Money minor={previewData.incomeMinor} currency={currency} /> ·{' '}
      {t('import.expense')}: <Money minor={previewData.expenseMinor} currency={currency} />
    </div>
  </div>
)}
```

- [ ] **Step 4: "Adjust columns" expander** — wrap the current step-2 controls in a `<details>` (or a toggle) seeded from the detected mapping; on change, re-`preview` with the explicit (non-null) mapping. Commit sends the detected-or-adjusted mapping.

- [ ] **Step 5: i18n** — add PL + EN keys: `detected`, `headerRow` (`row {{row}}`), `role_date`/`role_amount`/`role_description`/`role_debit`/`role_credit`, `income`, `expense`, `adjustColumns`.

- [ ] **Step 6: Frontend gate**

```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/frontend
npm run lint && npm test && npm run build
```
Expected: eslint clean (pre-existing warnings only), Vitest green (update `ImportPage.test.tsx` for the detect-first flow), `tsc -b` + vite build succeed.

- [ ] **Step 7: Commit** (after the user's go-ahead)

```bash
git add frontend/src/features/import/ frontend/src/lib/i18n.ts frontend/src/api/types.ts frontend/src/api/types.gen.ts
git commit -m "feat(frontend): detect-first CSV import (banner + adjust-columns fallback)"
```

---

## Task C: Boundary — verify + document

- [ ] **Step 1: One-off throwaway check** — pick an account, drop an mBank-style CSV (the fixture, or `~/Downloads/finance-demo-import.csv`), confirm the preview auto-maps with a correct banner + totals, commit, see the batch. Don't commit an E2E spec.
- [ ] **Step 2: Update `HANDOFF.md` + `CLAUDE.md`** (local-only): Smart CSV import delivered — encoding/header/column auto-detection, V16, detect-first UI. Note migration is now **V17** free. Backlog resumes at **E — saved views/filters**.
- [ ] **Step 3: Stop at the phase boundary.** Report green builds; push only when asked.

---

## Self-review notes (author)

- **Spec coverage:** encoding (A2), delimiter robustness (A3), header detection + dictionary (A4), column roles incl. Saldo guard / operation-date preference / multi-column description / sign inference (A5), DTO+entity+migration V16 (A6), optional-mapping wiring + preview sums (A7), fixture-oracle + isolation integration test (A8); frontend detect-first + banner + adjust-columns (B1–B2). Matches spec §3–7.
- **Placeholder scan:** none — every step carries real code or an exact command. The fixture is generated by a committed script step with a verified CP1250 assertion; the acceptance numbers are computed in spec §7 and asserted in A8.
- **Type consistency:** `ImportMapping` gains `headerRowIndex`/`descriptionIndexes` used identically across DTO ↔ entity ↔ `toMapping`/`upsertProfile` ↔ `ImportRowBuilder` ↔ `CsvMappingDetector`; `PreviewResponse` gains `incomeMinor`/`expenseMinor`/`mapping`/`detection` used identically in service ↔ test ↔ frontend. `DetectedMapping` (internal) vs `ImportMapping` (wire) kept distinct; the façade converts once.
- **Reuse:** `MoneyUtil`, `CsvDateParser`, `CsvDecoder`, `CsvParser`, the rules engine and dedupe/batch/undo path are all reused unchanged; detection only *produces a mapping*, so the committed behavior (locked rate, auto-categorize, dedupe, undoable batch) is untouched.
- **Risk / watch-items:** (1) `new ImportMapping(...)` arg count changes — every call site must be updated (compiler-guided). (2) `@RequestPart(required=false)` + `@Valid` on a null part — verify Spring doesn't 400 on absent part (it shouldn't; `@Valid` is skipped for null). (3) JaCoCo ≥ 0.85 — if the debit/credit branch of `MappingDetector` is uncovered, add a unit case. (4) Commons CSV `ignoreEmptyLines` means detection indices are parsed-row indices — the fixture oracle (header at parsed row 5) already accounts for this.
