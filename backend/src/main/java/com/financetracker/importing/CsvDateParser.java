package com.financetracker.importing;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * Flexible bank-date parsing — a faithful port of the prototype's {@code lib/csvDate.ts}. Tries the
 * given format (or the common Polish set for {@code "auto"}) and rejects rolled-over/lenient parses
 * like {@code 2026-13-40} with a strict resolver plus a round-trip check.
 *
 * <p>Formats are exposed in the prototype's date-fns tokens (e.g. {@code yyyy-MM-dd}); {@code y} is
 * translated to Java's proleptic-year {@code u} internally because {@link ResolverStyle#STRICT}
 * rejects year-of-era ({@code y}) without an era.
 */
public final class CsvDateParser {

  /** Formats tried in order for {@code "auto"} — exposed to the UI in date-fns tokens. */
  public static final List<String> AUTO_DATE_FORMATS =
      List.of("yyyy-MM-dd", "dd.MM.yyyy", "dd-MM-yyyy", "dd/MM/yyyy", "yyyy/MM/dd");

  private CsvDateParser() {}

  /** Parse into a {@link LocalDate}, or {@code null} when nothing matches. */
  public static LocalDate parseFlexible(String value, String format) {
    String s = value == null ? "" : value.trim();
    if (s.isEmpty()) {
      return null;
    }
    List<String> formats =
        (format == null || format.isBlank() || format.equals("auto"))
            ? AUTO_DATE_FORMATS
            : List.of(format);
    for (String pattern : formats) {
      DateTimeFormatter formatter =
          DateTimeFormatter.ofPattern(pattern.replace('y', 'u'))
              .withResolverStyle(ResolverStyle.STRICT);
      try {
        LocalDate date = LocalDate.parse(s, formatter);
        // Round-trip: a lenient/padded reparse that does not reproduce the input is not a match.
        if (formatter.format(date).equals(s)) {
          return date;
        }
      } catch (DateTimeParseException ignored) {
        // Try the next format.
      }
    }
    return null;
  }
}
