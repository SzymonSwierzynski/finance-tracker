package com.financetracker.importing.detect;

import com.financetracker.common.money.MoneyUtil;
import com.financetracker.importing.AmountMode;
import com.financetracker.importing.CsvDateParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assign column roles from a header row plus a data sample. Date prefers the operation date and is
 * confirmed by value-sniffing (which also picks the date format); amount excludes any balance
 * column (the Saldo guard) and infers signed-vs-debit/credit and the expense sign; description
 * collects every text-role column in header order. Pure and deterministic.
 */
public final class MappingDetector {

  private static final int SAMPLE = 20;

  /**
   * Counterparty tokens within the DESCRIPTION vocabulary that identify a sender/recipient name
   * rather than a transaction narrative. These columns are NOT collected as description — they are
   * counterparty metadata that the importer exposes separately. Listed in
   * HeaderDictionary.DESCRIPTION for role-scoring purposes but excluded here at the policy level.
   */
  private static final List<String> COUNTERPARTY =
      List.of("nadawca/odbiorca", "nadawca", "odbiorca", "kontrahent");

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
      if (isBalanceOrAccount(norm[c])
          || !HeaderDictionary.matches(norm[c], HeaderDictionary.DATE)) {
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

    // --- Description: primary text-role columns in header order, excluding counterparty/amount ---
    List<Integer> descriptionIndexes = new ArrayList<>();
    for (int c = 0; c < cols; c++) {
      if (isBalanceOrAccount(norm[c])
          || c == dateIndex
          || c == amountIndex
          || c == debitIndex
          || c == creditIndex
          || HeaderDictionary.matches(norm[c], COUNTERPARTY)) {
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
        dateIndex,
        dateFormat,
        mode,
        amountIndex,
        expenseIsNegative,
        debitIndex,
        creditIndex,
        descriptionIndexes,
        recognized);
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
