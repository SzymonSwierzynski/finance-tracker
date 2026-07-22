package com.financetracker.importing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parse CSV text into rows of raw string cells. Replaces the prototype's PapaParse: an unset
 * delimiter is auto-detected (Polish exports favour {@code ';'} because {@code ','} is the decimal
 * separator); quoted fields with embedded delimiters are handled by Commons CSV.
 */
public final class CsvParser {

  /** Delimiters tried when auto-detecting, in the order the prototype's exports use them. */
  private static final char[] CANDIDATE_DELIMITERS = {';', ',', '\t'};

  private CsvParser() {}

  public record ParsedCsv(List<List<String>> rows, char delimiter) {}

  /** Parse {@code text}; an empty {@code delimiter} triggers auto-detection. */
  public static ParsedCsv parse(String text, String delimiter) {
    char delim =
        (delimiter != null && !delimiter.isBlank()) ? delimiter.charAt(0) : detectDelimiter(text);
    CSVFormat format =
        CSVFormat.DEFAULT.builder().setDelimiter(delim).setIgnoreEmptyLines(true).build();
    try (CSVParser parser = CSVParser.parse(text, format)) {
      List<List<String>> rows = new ArrayList<>();
      for (CSVRecord record : parser) {
        List<String> row = new ArrayList<>(record.size());
        record.forEach(row::add);
        rows.add(row);
      }
      return new ParsedCsv(rows, delim);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse CSV", e);
    }
  }

  /** Pick the candidate that appears most on the first non-empty line (a PapaParse-style sniff). */
  private static char detectDelimiter(String text) {
    String firstLine = firstNonEmptyLine(text);
    char best = ',';
    int bestCount = -1;
    for (char candidate : CANDIDATE_DELIMITERS) {
      int count = countOccurrences(firstLine, candidate);
      if (count > bestCount) {
        bestCount = count;
        best = candidate;
      }
    }
    return best;
  }

  private static String firstNonEmptyLine(String text) {
    for (String line : text.split("\r\n|\r|\n")) {
      if (!line.isBlank()) {
        return line;
      }
    }
    return "";
  }

  private static int countOccurrences(String s, char c) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == c) {
        count++;
      }
    }
    return count;
  }
}
