package com.financetracker.importing.detect;

import com.financetracker.importing.CsvDecoder;
import com.financetracker.importing.CsvParser;
import com.financetracker.importing.CsvParser.ParsedCsv;
import com.financetracker.importing.dto.DetectionInfo;
import com.financetracker.importing.dto.ImportMapping;
import java.util.List;

/**
 * End-to-end auto-detection: bytes → encoding → parsed rows → header row → column roles → a
 * complete {@link ImportMapping} plus the {@link DetectionInfo} for the UI. Falls back gracefully
 * (no header found → treat row 0 as header) so preview always returns something.
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
            ? new DetectedMapping(
                0,
                "auto",
                com.financetracker.importing.AmountMode.SIGNED,
                2,
                true,
                -1,
                -1,
                List.of(1),
                java.util.Map.of())
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
    return new Detected(
        mapping,
        new DetectionInfo(encoding, hasHeader ? headerRow : null, cols.recognizedColumns()));
  }
}
