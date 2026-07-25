package com.financetracker.reporting;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryKind;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.reporting.dto.BreakdownResponse;
import com.financetracker.reporting.dto.BreakdownResponse.BreakdownChild;
import com.financetracker.reporting.dto.BreakdownResponse.BreakdownParent;
import com.financetracker.reporting.dto.CashflowResponse;
import com.financetracker.reporting.dto.CashflowResponse.CashflowBucket;
import com.financetracker.reporting.dto.CategoryTrendResponse;
import com.financetracker.reporting.dto.CategoryTrendResponse.CategorySeries;
import com.financetracker.reporting.dto.CategoryTrendResponse.CategoryTrendBucket;
import com.financetracker.reporting.dto.ComparisonResponse;
import com.financetracker.reporting.dto.ComparisonResponse.Delta;
import com.financetracker.reporting.dto.ComparisonResponse.PeriodSummary;
import com.financetracker.reporting.dto.SummaryResponse;
import com.financetracker.reporting.dto.TrendComparisonResponse;
import com.financetracker.reporting.dto.TrendComparisonResponse.CategoryMover;
import com.financetracker.reporting.dto.TrendResponse;
import com.financetracker.reporting.dto.TrendResponse.TrendBucket;
import com.financetracker.settings.SettingsService;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionRepository.CategorySumRow;
import com.financetracker.transaction.TransactionRepository.PeriodCategorySumRow;
import com.financetracker.transaction.TransactionRepository.PeriodSumRow;
import com.financetracker.transaction.TransactionRepository.SummaryRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read models for "where my money goes". Totals are computed in SQL and returned in base-currency
 * minor units; correctness is pinned by fixed-fixture tests asserting to the grosz.
 */
@Service
public class ReportingService {

  /** Slate-400 — the fixed colour for the Uncategorized slice (matches the prototype). */
  private static final String UNCATEGORIZED_COLOR = "#94a3b8";

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final SettingsService settingsService;

  public ReportingService(
      TransactionRepository transactionRepository,
      CategoryRepository categoryRepository,
      SettingsService settingsService) {
    this.transactionRepository = transactionRepository;
    this.categoryRepository = categoryRepository;
    this.settingsService = settingsService;
  }

  @Transactional(readOnly = true)
  public SummaryResponse summary(long userId, LocalDate from, LocalDate to) {
    requireRange(from, to);
    PeriodSummary s = periodSummary(userId, from, to);
    return new SummaryResponse(
        from,
        to,
        settingsService.reportingCurrency(userId),
        s.incomeMinor(),
        s.expenseMinor(),
        s.netMinor());
  }

  /**
   * Period-over-period comparison: the requested range vs the same range shifted back one {@code
   * mode} unit (month or year), with the per-total delta. Reuses the summary aggregation for both
   * periods; percentage change is derived at the display edge.
   */
  @Transactional(readOnly = true)
  public ComparisonResponse comparison(long userId, LocalDate from, LocalDate to, String mode) {
    requireRange(from, to);
    String normalized = (mode == null || mode.isBlank()) ? "month" : mode.toLowerCase(Locale.ROOT);
    Period shift =
        switch (normalized) {
          case "month" -> Period.ofMonths(1);
          case "year" -> Period.ofYears(1);
          default -> throw new UnprocessableEntityException("mode must be 'month' or 'year'.");
        };
    PeriodSummary current = periodSummary(userId, from, to);
    PeriodSummary previous = periodSummary(userId, from.minus(shift), to.minus(shift));
    Delta delta =
        new Delta(
            current.incomeMinor() - previous.incomeMinor(),
            current.expenseMinor() - previous.expenseMinor(),
            current.netMinor() - previous.netMinor());
    return new ComparisonResponse(
        settingsService.reportingCurrency(userId), normalized, current, previous, delta);
  }

  /**
   * Trends period-comparison: the requested range vs the immediately-preceding range of equal
   * length, with the per-total delta and expense-category movers rolled up to top-level parents and
   * ordered by absolute change. Reuses the summary aggregation and {@code sumByCategory} query.
   */
  @Transactional(readOnly = true)
  public TrendComparisonResponse trendComparison(long userId, LocalDate from, LocalDate to) {
    requireRange(from, to);
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    LocalDate prevTo = from.minusDays(1);
    LocalDate prevFrom = prevTo.minusDays(days - 1);

    PeriodSummary current = periodSummary(userId, from, to);
    PeriodSummary previous = periodSummary(userId, prevFrom, prevTo);
    Delta delta =
        new Delta(
            current.incomeMinor() - previous.incomeMinor(),
            current.expenseMinor() - previous.expenseMinor(),
            current.netMinor() - previous.netMinor());

    Map<Long, Category> byId =
        categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
            .collect(Collectors.toMap(Category::getId, Function.identity()));
    // Null key = the Uncategorized bucket (LinkedHashMap permits a single null key).
    Map<Long, MoverAcc> movers = new LinkedHashMap<>();
    accumulateExpenseByParent(userId, from, to, byId, movers, true);
    accumulateExpenseByParent(userId, prevFrom, prevTo, byId, movers, false);

    List<CategoryMover> out =
        movers.values().stream()
            .map(m -> new CategoryMover(m.categoryId, m.name, m.color, m.current, m.previous))
            .sorted((a, b) -> Long.compare(Math.abs(b.deltaMinor()), Math.abs(a.deltaMinor())))
            .toList();

    return new TrendComparisonResponse(
        settingsService.reportingCurrency(userId), current, previous, delta, out);
  }

  /**
   * Folds one range's expense rows into per-top-level-parent movers (subcategories roll up to their
   * parent; null/unknown category -> Uncategorized). {@code isCurrent} routes the sum into the
   * current or previous side of each accumulator.
   */
  private void accumulateExpenseByParent(
      long userId,
      LocalDate from,
      LocalDate to,
      Map<Long, Category> byId,
      Map<Long, MoverAcc> movers,
      boolean isCurrent) {
    for (CategorySumRow row : transactionRepository.sumByCategory(userId, from, to, "expense")) {
      long base = baseMinorOf(row.getBaseMinor());
      Long catId = row.getCategoryId();
      Category cat = catId == null ? null : byId.get(catId);

      Long key;
      String name;
      String color;
      if (cat == null) {
        key = null;
        name = "Uncategorized";
        color = UNCATEGORIZED_COLOR;
      } else if (cat.getParentId() == null) {
        key = cat.getId();
        name = cat.getName();
        color = cat.getColor();
      } else {
        Category parent = byId.get(cat.getParentId());
        key = parent != null ? parent.getId() : cat.getParentId();
        name = parent != null ? parent.getName() : "Unknown";
        color = parent != null ? parent.getColor() : UNCATEGORIZED_COLOR;
      }

      MoverAcc acc = movers.computeIfAbsent(key, k -> new MoverAcc(k, name, color));
      if (isCurrent) {
        acc.current += base;
      } else {
        acc.previous += base;
      }
    }
  }

  /** Income/expense/net (base minor units) for one range — the shared summary aggregation. */
  private PeriodSummary periodSummary(long userId, LocalDate from, LocalDate to) {
    long incomeMinor = 0L;
    long expenseMinor = 0L;
    for (SummaryRow row : transactionRepository.summarize(userId, from, to)) {
      long base = baseMinorOf(row.getBaseMinor());
      if ("income".equals(row.getType())) {
        incomeMinor = base;
      } else if ("expense".equals(row.getType())) {
        expenseMinor = base;
      }
    }
    return new PeriodSummary(from, to, incomeMinor, expenseMinor, incomeMinor - expenseMinor);
  }

  /**
   * Two-level category breakdown for one kind. Subcategory spend rolls up to its parent; a parent
   * with both direct and subcategory spend gets a synthetic "(direct)" child. Uncategorized spend
   * (null category, or a category outside the user's set) lands in its own slice.
   */
  @Transactional(readOnly = true)
  public BreakdownResponse breakdown(
      long userId, LocalDate from, LocalDate to, CategoryKind kind, Long parentId) {
    requireRange(from, to);
    String currency = settingsService.reportingCurrency(userId);

    Map<Long, Category> byId =
        categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
            .collect(Collectors.toMap(Category::getId, Function.identity()));

    Map<Long, ParentAcc> parents = new LinkedHashMap<>();
    ParentAcc uncategorized = null;
    long total = 0L;
    long count = 0L;

    for (CategorySumRow row : transactionRepository.sumByCategory(userId, from, to, kind.value())) {
      long base = baseMinorOf(row.getBaseMinor());
      total += base;
      count += row.getTxnCount();

      Long catId = row.getCategoryId();
      Category cat = catId == null ? null : byId.get(catId);
      if (cat == null) {
        if (uncategorized == null) {
          uncategorized = new ParentAcc(null, "Uncategorized", UNCATEGORIZED_COLOR);
        }
        uncategorized.direct += base;
        uncategorized.base += base;
      } else if (cat.getParentId() == null) {
        ParentAcc acc =
            parents.computeIfAbsent(
                cat.getId(), k -> new ParentAcc(cat.getId(), cat.getName(), cat.getColor()));
        acc.direct += base;
        acc.base += base;
      } else {
        Category parent = byId.get(cat.getParentId());
        ParentAcc acc =
            parents.computeIfAbsent(
                cat.getParentId(),
                k ->
                    new ParentAcc(
                        parent != null ? parent.getId() : cat.getParentId(),
                        parent != null ? parent.getName() : "Unknown",
                        parent != null ? parent.getColor() : UNCATEGORIZED_COLOR));
        acc.children.add(new ChildAcc(cat.getId(), cat.getName(), base));
        acc.base += base;
      }
    }

    List<ParentAcc> accs = new ArrayList<>(parents.values());
    if (uncategorized != null) {
      accs.add(uncategorized);
    }

    List<BreakdownParent> out = new ArrayList<>();
    for (ParentAcc acc : accs) {
      List<BreakdownChild> children = new ArrayList<>();
      for (ChildAcc child : acc.children) {
        children.add(
            new BreakdownChild(child.id, child.name, child.base, share(child.base, acc.base)));
      }
      // Surface the parent's own direct spend as a slice when it also has subcategories.
      if (acc.categoryId != null && acc.direct > 0 && !acc.children.isEmpty()) {
        children.add(
            new BreakdownChild(
                acc.categoryId, acc.name + " (direct)", acc.direct, share(acc.direct, acc.base)));
      }
      children.sort((a, b) -> Long.compare(b.baseMinor(), a.baseMinor()));
      out.add(
          new BreakdownParent(
              acc.categoryId, acc.name, acc.color, acc.base, share(acc.base, total), children));
    }
    out.sort((a, b) -> Long.compare(b.baseMinor(), a.baseMinor()));

    if (parentId != null) {
      out = out.stream().filter(p -> Objects.equals(p.categoryId(), parentId)).toList();
    }

    return new BreakdownResponse(kind, from, to, currency, total, count, out);
  }

  /**
   * Income + expense per time bucket over the range (zero-filled), for a spending-over-time chart.
   */
  @Transactional(readOnly = true)
  public TrendResponse trend(long userId, LocalDate from, LocalDate to, String interval) {
    requireRange(from, to);
    Interval iv = Interval.parse(interval);
    List<TrendBucket> buckets = new ArrayList<>();
    for (Map.Entry<String, long[]> e : incomeExpenseByPeriod(userId, from, to, iv).entrySet()) {
      buckets.add(new TrendBucket(e.getKey(), e.getValue()[0], e.getValue()[1]));
    }
    return new TrendResponse(
        from, to, iv.value, settingsService.reportingCurrency(userId), buckets);
  }

  /** Income vs expense per time bucket with a running net accumulated across the range. */
  @Transactional(readOnly = true)
  public CashflowResponse cashflow(long userId, LocalDate from, LocalDate to, String interval) {
    requireRange(from, to);
    Interval iv = Interval.parse(interval);
    List<CashflowBucket> buckets = new ArrayList<>();
    long running = 0L;
    for (Map.Entry<String, long[]> e : incomeExpenseByPeriod(userId, from, to, iv).entrySet()) {
      long income = e.getValue()[0];
      long expense = e.getValue()[1];
      long net = income - expense;
      running += net;
      buckets.add(new CashflowBucket(e.getKey(), income, expense, net, running));
    }
    return new CashflowResponse(
        from, to, iv.value, settingsService.reportingCurrency(userId), buckets);
  }

  /**
   * period -> [income, expense] in base minor units, zero-filled across [from, to] and
   * chronological (TreeMap). Every calendar day seeds its bucket key so gaps render as zero; the
   * SQL fills the non-empty ones.
   */
  private Map<String, long[]> incomeExpenseByPeriod(
      long userId, LocalDate from, LocalDate to, Interval iv) {
    Map<String, long[]> byPeriod = new TreeMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      byPeriod.computeIfAbsent(iv.key(d), k -> new long[2]);
    }
    for (PeriodSumRow row : transactionRepository.sumByPeriod(userId, from, to, iv.fmt)) {
      long[] slot = byPeriod.computeIfAbsent(row.getPeriod(), k -> new long[2]);
      long base = baseMinorOf(row.getBaseMinor());
      if ("income".equals(row.getType())) {
        slot[0] = base;
      } else if ("expense".equals(row.getType())) {
        slot[1] = base;
      }
    }
    return byPeriod;
  }

  /** Expense (or income) over time stacked by top-level category, for a stacked chart. */
  @Transactional(readOnly = true)
  public CategoryTrendResponse categoryTrend(
      long userId, LocalDate from, LocalDate to, String interval, CategoryKind kind) {
    requireRange(from, to);
    Interval iv = Interval.parse(interval);
    Map<Long, Category> byId =
        categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
            .collect(Collectors.toMap(Category::getId, Function.identity()));

    Map<String, Map<String, Long>> amountsByPeriod = new TreeMap<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      amountsByPeriod.computeIfAbsent(iv.key(d), k -> new HashMap<>());
    }

    Map<String, SeriesAcc> seriesByKey = new LinkedHashMap<>();
    for (PeriodCategorySumRow row :
        transactionRepository.sumByPeriodAndCategory(userId, from, to, iv.fmt, kind.value())) {
      long base = baseMinorOf(row.getBaseMinor());
      Category cat = row.getCategoryId() == null ? null : byId.get(row.getCategoryId());

      String key;
      Long seriesCatId;
      String name;
      String color;
      if (cat == null) {
        key = "uncategorized";
        seriesCatId = null;
        name = "Uncategorized";
        color = UNCATEGORIZED_COLOR;
      } else if (cat.getParentId() == null) {
        seriesCatId = cat.getId();
        key = String.valueOf(seriesCatId);
        name = cat.getName();
        color = cat.getColor();
      } else {
        Category parent = byId.get(cat.getParentId());
        seriesCatId = parent != null ? parent.getId() : cat.getParentId();
        key = String.valueOf(seriesCatId);
        name = parent != null ? parent.getName() : "Unknown";
        color = parent != null ? parent.getColor() : UNCATEGORIZED_COLOR;
      }

      SeriesAcc acc =
          seriesByKey.computeIfAbsent(key, k -> new SeriesAcc(seriesCatId, name, color));
      acc.total += base;
      amountsByPeriod
          .computeIfAbsent(row.getPeriod(), k -> new HashMap<>())
          .merge(key, base, Long::sum);
    }

    List<CategorySeries> series =
        seriesByKey.values().stream()
            .sorted((a, b) -> Long.compare(b.total, a.total))
            .map(a -> new CategorySeries(a.categoryId, a.name, a.color))
            .toList();
    List<CategoryTrendBucket> buckets = new ArrayList<>();
    for (Map.Entry<String, Map<String, Long>> e : amountsByPeriod.entrySet()) {
      buckets.add(new CategoryTrendBucket(e.getKey(), e.getValue()));
    }
    return new CategoryTrendResponse(
        from, to, iv.value, settingsService.reportingCurrency(userId), kind, series, buckets);
  }

  /**
   * Narrows a SQL aggregate to integer minor units. The queries already {@code round()} each row
   * before summing, so the value arrives with scale 0 — but an unqualified {@code setScale(0)}
   * would throw the moment that stopped being true. Rounding half-up explicitly keeps the failure
   * mode a (correct) rounding rather than an ArithmeticException, and matches how the rest of the
   * app converts to base.
   */
  private static long baseMinorOf(BigDecimal aggregate) {
    return aggregate.setScale(0, RoundingMode.HALF_UP).longValueExact();
  }

  private static void requireRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new UnprocessableEntityException("'from' must not be after 'to'.");
    }
  }

  private static double share(long part, long whole) {
    return whole == 0 ? 0.0 : (double) part / (double) whole;
  }

  /** Mutable accumulator for a top-level slice while folding. */
  private static final class ParentAcc {
    private final Long categoryId;
    private final String name;
    private final String color;
    private final List<ChildAcc> children = new ArrayList<>();
    private long direct;
    private long base;

    private ParentAcc(Long categoryId, String name, String color) {
      this.categoryId = categoryId;
      this.name = name;
      this.color = color;
    }
  }

  private record ChildAcc(Long id, String name, long base) {}

  /** Mutable accumulator for a top-level expense mover (current vs previous spend). */
  private static final class MoverAcc {
    private final Long categoryId;
    private final String name;
    private final String color;
    private long current;
    private long previous;

    private MoverAcc(Long categoryId, String name, String color) {
      this.categoryId = categoryId;
      this.name = name;
      this.color = color;
    }
  }

  /** Mutable accumulator for a category-trend series (top-level category) while folding. */
  private static final class SeriesAcc {
    private final Long categoryId;
    private final String name;
    private final String color;
    private long total;

    private SeriesAcc(Long categoryId, String name, String color) {
      this.categoryId = categoryId;
      this.name = name;
      this.color = color;
    }
  }

  /** Time-bucket granularity, its Postgres {@code to_char} pattern, and a matching Java key. */
  private enum Interval {
    MONTH("month", "YYYY-MM"),
    WEEK("week", "IYYY-IW");

    private final String value;
    private final String fmt;

    Interval(String value, String fmt) {
      this.value = value;
      this.fmt = fmt;
    }

    static Interval parse(String interval) {
      if (interval == null || interval.isBlank() || interval.equalsIgnoreCase("month")) {
        return MONTH;
      }
      if (interval.equalsIgnoreCase("week")) {
        return WEEK;
      }
      throw new UnprocessableEntityException("interval must be 'month' or 'week'.");
    }

    /** The bucket key for a date, matching what {@code to_char(date, fmt)} produces in SQL. */
    String key(LocalDate d) {
      if (this == WEEK) {
        return String.format(
            Locale.ROOT,
            "%04d-%02d",
            d.get(IsoFields.WEEK_BASED_YEAR),
            d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
      }
      return String.format(Locale.ROOT, "%04d-%02d", d.getYear(), d.getMonthValue());
    }
  }
}
