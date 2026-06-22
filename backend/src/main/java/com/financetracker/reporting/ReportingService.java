package com.financetracker.reporting;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryKind;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.reporting.dto.BreakdownResponse;
import com.financetracker.reporting.dto.BreakdownResponse.BreakdownChild;
import com.financetracker.reporting.dto.BreakdownResponse.BreakdownParent;
import com.financetracker.reporting.dto.SummaryResponse;
import com.financetracker.settings.SettingsService;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionRepository.CategorySumRow;
import com.financetracker.transaction.TransactionRepository.SummaryRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    List<SummaryRow> rows = transactionRepository.summarize(userId, from, to);

    long incomeMinor = 0L;
    long expenseMinor = 0L;
    for (SummaryRow row : rows) {
      long base = row.getBaseMinor().setScale(0).longValueExact();
      if ("income".equals(row.getType())) {
        incomeMinor = base;
      } else if ("expense".equals(row.getType())) {
        expenseMinor = base;
      }
    }

    String currency = settingsService.reportingCurrency(userId);
    return new SummaryResponse(
        from, to, currency, incomeMinor, expenseMinor, incomeMinor - expenseMinor);
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
      long base = row.getBaseMinor().setScale(0).longValueExact();
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
}
