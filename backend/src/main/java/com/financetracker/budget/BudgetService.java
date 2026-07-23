package com.financetracker.budget;

import com.financetracker.budget.dto.BudgetResponse;
import com.financetracker.budget.dto.BudgetsResponse;
import com.financetracker.budget.dto.BudgetsResponse.BudgetProgress;
import com.financetracker.budget.dto.CreateBudgetRequest;
import com.financetracker.budget.dto.UpdateBudgetRequest;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryKind;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.error.ConflictException;
import com.financetracker.common.error.NotFoundException;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.settings.SettingsService;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionRepository.CategorySumRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget business logic: ownership + expense-only + one-per-category invariants on the budget, and
 * per-month progress. Progress compares base-currency spend against the limit, rolling subcategory
 * spend into a parent budget the same way the breakdown does (CLAUDE.md §9); correctness is pinned
 * by fixed-fixture tests.
 */
@Service
public class BudgetService {

  private final BudgetRepository budgetRepository;
  private final CategoryRepository categoryRepository;
  private final TransactionRepository transactionRepository;
  private final SettingsService settingsService;

  public BudgetService(
      BudgetRepository budgetRepository,
      CategoryRepository categoryRepository,
      TransactionRepository transactionRepository,
      SettingsService settingsService) {
    this.budgetRepository = budgetRepository;
    this.categoryRepository = categoryRepository;
    this.transactionRepository = transactionRepository;
    this.settingsService = settingsService;
  }

  @Transactional
  public BudgetResponse create(long userId, CreateBudgetRequest request) {
    requireExpenseCategory(userId, request.categoryId());
    if (budgetRepository.existsByUserIdAndCategoryId(userId, request.categoryId())) {
      throw new ConflictException("This category already has a budget.");
    }
    Budget budget = new Budget();
    budget.setUserId(userId);
    budget.setCategoryId(request.categoryId());
    budget.setAmountMinor(request.amountMinor());
    return toResponse(budgetRepository.save(budget));
  }

  @Transactional
  public BudgetResponse update(long userId, long id, UpdateBudgetRequest request) {
    Budget budget = requireOwned(userId, id);
    requireCurrentVersion(budget, request.version());
    budget.setAmountMinor(request.amountMinor());
    return toResponse(budgetRepository.saveAndFlush(budget));
  }

  @Transactional
  public void delete(long userId, long id) {
    budgetRepository.delete(requireOwned(userId, id));
  }

  /**
   * The user's budgets with progress for {@code month} ({@code YYYY-MM}; defaults to this month).
   */
  @Transactional(readOnly = true)
  public BudgetsResponse list(long userId, String month) {
    YearMonth ym = parseMonth(month);
    List<Budget> budgets = budgetRepository.findByUserId(userId);
    Map<Long, Category> byId =
        categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
            .collect(Collectors.toMap(Category::getId, Function.identity()));
    Map<Long, Long> spentByCategory = rollUpExpenses(userId, ym, byId);

    List<BudgetProgress> items =
        budgets.stream()
            .map(b -> toProgress(b, byId.get(b.getCategoryId()), spentByCategory))
            .sorted(
                Comparator.comparing(BudgetProgress::categoryName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    return new BudgetsResponse(ym.toString(), settingsService.reportingCurrency(userId), items);
  }

  /**
   * Base-currency expense spend for the month, keyed by category id, with each row also folded into
   * its parent — so a parent budget sees its direct spend plus every subcategory's. Uncategorized
   * spend (null category, or one outside the user's set) has no budget and is dropped.
   */
  private Map<Long, Long> rollUpExpenses(long userId, YearMonth ym, Map<Long, Category> byId) {
    Map<Long, Long> spent = new HashMap<>();
    for (CategorySumRow row :
        transactionRepository.sumByCategory(
            userId, ym.atDay(1), ym.atEndOfMonth(), CategoryKind.EXPENSE.value())) {
      Category cat = row.getCategoryId() == null ? null : byId.get(row.getCategoryId());
      if (cat == null) {
        continue;
      }
      long base = baseMinorOf(row.getBaseMinor());
      spent.merge(cat.getId(), base, Long::sum);
      if (cat.getParentId() != null) {
        spent.merge(cat.getParentId(), base, Long::sum);
      }
    }
    return spent;
  }

  private BudgetProgress toProgress(Budget b, Category category, Map<Long, Long> spentByCategory) {
    long spent = spentByCategory.getOrDefault(b.getCategoryId(), 0L);
    return new BudgetProgress(
        b.getId(),
        b.getCategoryId(),
        category == null ? "" : category.getName(),
        category == null ? "" : category.getColor(),
        b.getAmountMinor(),
        spent,
        b.getAmountMinor() - spent,
        spent > b.getAmountMinor(),
        b.getVersion());
  }

  private void requireExpenseCategory(long userId, long categoryId) {
    Category category =
        categoryRepository
            .findByIdAndUserId(categoryId, userId)
            .orElseThrow(() -> NotFoundException.of("Category", categoryId));
    if (category.getKind() != CategoryKind.EXPENSE) {
      throw new UnprocessableEntityException("Budgets can only be set on expense categories.");
    }
  }

  private Budget requireOwned(long userId, long id) {
    return budgetRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> NotFoundException.of("Budget", id));
  }

  private static YearMonth parseMonth(String month) {
    if (month == null || month.isBlank()) {
      return YearMonth.from(LocalDate.now());
    }
    try {
      return YearMonth.parse(month);
    } catch (DateTimeParseException e) {
      throw new UnprocessableEntityException("month must be in YYYY-MM format.");
    }
  }

  private static void requireCurrentVersion(Budget budget, long expectedVersion) {
    if (budget.getVersion() == null || budget.getVersion() != expectedVersion) {
      throw new ConflictException("Budget was modified concurrently; reload and retry.");
    }
  }

  private static long baseMinorOf(BigDecimal aggregate) {
    return aggregate.setScale(0, RoundingMode.HALF_UP).longValueExact();
  }

  private static BudgetResponse toResponse(Budget b) {
    return new BudgetResponse(b.getId(), b.getCategoryId(), b.getAmountMinor(), b.getVersion());
  }
}
