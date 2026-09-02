package com.financetracker.importing.detect;

import java.util.List;

/**
 * Locate the header row in parsed CSV rows, skipping any preamble/summary. Scans the first 50 rows
 * and scores each by how many cells hit the {@link HeaderDictionary} role vocabularies; the top row
 * wins when it scores at least {@value #MIN_SCORE}. Returns the 0-based parsed row index, or -1
 * when nothing looks like a header (caller falls back to the hasHeader heuristic).
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
