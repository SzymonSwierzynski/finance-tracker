package com.financetracker.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Money utilities — INTEGER MINOR UNITS ONLY.
 *
 * <p>Money is stored as {@code long} minor units (grosze / cents): 19.99 PLN -> 1999. We never
 * store or do arithmetic on floats; {@link BigDecimal} appears only transiently inside FX/rounding
 * math and is converted back to {@code long} before it leaves this class. The only place a value is
 * divided by 100 is the display formatter.
 *
 * <p>This is a faithful port of the prototype's {@code lib/money.ts}; its test cases are ported
 * verbatim. Phase 1 assumes 2-decimal currencies — the exponent is centralized here so a
 * per-currency table (e.g. JPY = 0) can be introduced later without touching call sites.
 */
public final class MoneyUtil {

  public static final int MINOR_UNIT_EXPONENT = 2;
  private static final long MINOR_UNIT_FACTOR = 100L; // 10^MINOR_UNIT_EXPONENT

  // (?U) = UNICODE_CHARACTER_CLASS so \s matches NBSP (U+00A0), narrow NBSP (U+202F) and thin
  // space — Java's default \s does NOT, unlike JavaScript's. These are only ever thousands
  // separators in bank exports, so they are stripped wholesale (matches the prototype).
  private static final Pattern UNICODE_SPACE = Pattern.compile("(?U)\\s");
  private static final Pattern DIGITS_AND_SEPARATORS = Pattern.compile("^[0-9.,]+$");
  private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9]*$");

  private MoneyUtil() {}

  /**
   * Parse a human-typed (or bank-exported) amount into integer minor units. Tolerant of the formats
   * a Polish user actually produces: dot or comma decimals, space/NBSP/EU/US thousands separators,
   * an optional leading sign. Fractions longer than the minor-unit exponent are rounded half-up.
   * Returns {@code null} for anything that isn't a parseable number.
   */
  public static Long parseAmountToMinor(String input) {
    if (input == null) {
      return null;
    }
    String s = input.trim();
    if (s.isEmpty()) {
      return null;
    }

    boolean negative = false;
    if (s.startsWith("-")) {
      negative = true;
      s = s.substring(1);
    } else if (s.startsWith("+")) {
      s = s.substring(1);
    }

    s = UNICODE_SPACE.matcher(s).replaceAll("");
    if (s.isEmpty()) {
      return null;
    }
    if (!DIGITS_AND_SEPARATORS.matcher(s).matches()) {
      return null;
    }

    int commaCount = count(s, ',');
    int dotCount = count(s, '.');

    // Decide which character is the decimal separator (if any).
    Character decimalSep;
    if (commaCount > 0 && dotCount > 0) {
      // Whichever appears last is the decimal separator; the other is thousands.
      decimalSep = s.lastIndexOf(',') > s.lastIndexOf('.') ? ',' : '.';
    } else if (commaCount == 1) {
      decimalSep = ',';
    } else if (dotCount == 1) {
      decimalSep = '.';
    } else {
      // None, or several of one kind (e.g. "1.234.567") -> all thousands.
      decimalSep = null;
    }

    String intPart;
    String fracPart;
    if (decimalSep == null) {
      intPart = s.replaceAll("[.,]", "");
      fracPart = "";
    } else {
      char otherSep = decimalSep == ',' ? '.' : ',';
      String cleaned = remove(s, otherSep); // strip thousands separators
      int idx = cleaned.lastIndexOf(decimalSep);
      intPart = remove(cleaned.substring(0, idx), decimalSep);
      fracPart = cleaned.substring(idx + 1);
    }

    if (intPart.isEmpty() && fracPart.isEmpty()) {
      return null;
    }
    if (!DIGITS_ONLY.matcher(intPart).matches() || !DIGITS_ONLY.matcher(fracPart).matches()) {
      return null;
    }

    long intValue = intPart.isEmpty() ? 0L : Long.parseLong(intPart);
    String fracHead =
        padEnd(
            fracPart.length() > MINOR_UNIT_EXPONENT
                ? fracPart.substring(0, MINOR_UNIT_EXPONENT)
                : fracPart,
            MINOR_UNIT_EXPONENT);
    long fracValue = Long.parseLong(fracHead);
    boolean roundUp =
        fracPart.length() > MINOR_UNIT_EXPONENT
            && (fracPart.charAt(MINOR_UNIT_EXPONENT) - '0') >= 5;

    long minor = intValue * MINOR_UNIT_FACTOR + fracValue + (roundUp ? 1 : 0);
    return negative ? -minor : minor;
  }

  /**
   * Convert a native-currency amount into base (reporting) minor units. Matches the data model:
   * base value = round(amountMinor * rateToBase), half-up. {@code rateToBase} is a {@link
   * BigDecimal} (the only NUMERIC in the model); the result is a {@code long}.
   */
  public static long toBaseMinor(long amountMinor, BigDecimal rateToBase) {
    return BigDecimal.valueOf(amountMinor)
        .multiply(rateToBase)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact();
  }

  /** Sum integer minor-unit values (exact integer arithmetic). */
  public static long sumMinor(List<Long> values) {
    long total = 0L;
    for (Long v : values) {
      total += v;
    }
    return total;
  }

  /** True when the input parses to a positive amount (what a transaction entry needs). */
  public static boolean isValidAmountInput(String input) {
    Long minor = parseAmountToMinor(input);
    return minor != null && minor > 0;
  }

  /**
   * Format integer minor units for display with a currency symbol/code. This is the only place a
   * stored value is divided by 100, purely for rendering. Backends rarely format money (reports
   * return integers, the frontend formats at the edge) — this exists for exports and logs.
   */
  public static String formatMinor(long minor, String currencyCode, Locale locale) {
    NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
    nf.setCurrency(Currency.getInstance(currencyCode));
    return nf.format(toMajor(minor));
  }

  /** Format integer minor units as a plain grouped decimal, no currency marker. */
  public static String formatMinorPlain(long minor, Locale locale) {
    NumberFormat nf = NumberFormat.getNumberInstance(locale);
    nf.setMinimumFractionDigits(MINOR_UNIT_EXPONENT);
    nf.setMaximumFractionDigits(MINOR_UNIT_EXPONENT);
    nf.setGroupingUsed(true);
    return nf.format(toMajor(minor));
  }

  private static BigDecimal toMajor(long minor) {
    return BigDecimal.valueOf(minor).movePointLeft(MINOR_UNIT_EXPONENT);
  }

  private static int count(String s, char c) {
    int n = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == c) {
        n++;
      }
    }
    return n;
  }

  private static String remove(String s, char c) {
    return s.replace(String.valueOf(c), "");
  }

  private static String padEnd(String s, int len) {
    StringBuilder sb = new StringBuilder(s);
    while (sb.length() < len) {
      sb.append('0');
    }
    return sb.toString();
  }
}
