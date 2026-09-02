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

  /**
   * Choose the delimiter that partitions the file most consistently. Score each candidate across
   * the first 40 non-empty lines by how many lines share the modal occurrence-count (consistency),
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
      if (consistency > bestConsistency || (consistency == bestConsistency && modal > bestModal)) {
        bestConsistency = consistency;
        bestModal = modal;
        best = candidate;
      }
    }
    return best;
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
