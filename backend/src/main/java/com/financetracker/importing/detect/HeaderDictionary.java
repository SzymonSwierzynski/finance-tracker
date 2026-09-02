package com.financetracker.importing.detect;

import java.util.List;
import java.util.Locale;

/**
 * PL + EN header-name vocabulary for column-role detection — the single tuning surface for new
 * banks (no per-bank presets). Cells are normalized before matching: leading '#'/quotes stripped,
 * Polish lower-cased, whitespace collapsed.
 */
public final class HeaderDictionary {

  private static final Locale PL = Locale.forLanguageTag("pl");

  public static final List<String> DATE =
      List.of(
          "data operacji", "data transakcji", "data waluty", "data księgowania", "data", "date");
  public static final List<String> AMOUNT = List.of("kwota operacji", "kwota", "amount");
  public static final List<String> DEBIT =
      List.of("kwota obciążenia", "obciążenia", "wypłata", "debit");
  public static final List<String> CREDIT = List.of("kwota uznania", "uznania", "wpłata", "credit");
  public static final List<String> DESCRIPTION =
      List.of(
          "opis operacji",
          "opis",
          "tytuł operacji",
          "tytuł",
          "nadawca/odbiorca",
          "nadawca",
          "odbiorca",
          "kontrahent",
          "szczegóły",
          "description",
          "details",
          "title");
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
