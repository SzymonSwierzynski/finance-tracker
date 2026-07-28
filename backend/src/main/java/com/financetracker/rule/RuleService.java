package com.financetracker.rule;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryKind;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.error.ConflictException;
import com.financetracker.common.error.NotFoundException;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.rule.RuleMatcher.MatchableRule;
import com.financetracker.rule.dto.ApplyRulesResponse;
import com.financetracker.rule.dto.CreateRuleRequest;
import com.financetracker.rule.dto.RuleResponse;
import com.financetracker.rule.dto.UpdateRuleRequest;
import com.financetracker.transaction.Transaction;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule business logic: ownership, category-reference validation, and applying rules over a user's
 * uncategorized transactions. A matched category is assigned only when its kind matches the
 * transaction's type, preserving the same category/type invariant the transaction service enforces
 * on manual entry (transfers are never categorized and are excluded).
 */
@Service
public class RuleService {

  private static final List<TransactionType> CATEGORIZABLE =
      List.of(TransactionType.EXPENSE, TransactionType.INCOME);

  private final RuleRepository ruleRepository;
  private final CategoryRepository categoryRepository;
  private final TransactionRepository transactionRepository;

  public RuleService(
      RuleRepository ruleRepository,
      CategoryRepository categoryRepository,
      TransactionRepository transactionRepository) {
    this.ruleRepository = ruleRepository;
    this.categoryRepository = categoryRepository;
    this.transactionRepository = transactionRepository;
  }

  @Transactional
  public RuleResponse create(long userId, CreateRuleRequest request) {
    requireOwnedCategory(userId, request.categoryId());
    Rule rule = new Rule();
    rule.setUserId(userId);
    rule.setPattern(request.pattern().trim());
    rule.setCategoryId(request.categoryId());
    rule.setPriority(request.priority() == null ? 0 : request.priority());
    return toResponse(ruleRepository.save(rule));
  }

  @Transactional(readOnly = true)
  public List<RuleResponse> list(long userId) {
    return ruleRepository.findByUserIdOrderByPriorityDescPatternAsc(userId).stream()
        .map(RuleService::toResponse)
        .toList();
  }

  @Transactional
  public RuleResponse update(long userId, long id, UpdateRuleRequest request) {
    Rule rule = requireOwned(userId, id);
    requireCurrentVersion(rule, request.version());
    if (request.pattern() != null) {
      String pattern = request.pattern().trim();
      if (pattern.isEmpty()) {
        throw new UnprocessableEntityException("A rule pattern must not be blank.");
      }
      rule.setPattern(pattern);
    }
    if (request.categoryId() != null) {
      requireOwnedCategory(userId, request.categoryId());
      rule.setCategoryId(request.categoryId());
    }
    if (request.priority() != null) {
      rule.setPriority(request.priority());
    }
    return toResponse(ruleRepository.saveAndFlush(rule));
  }

  @Transactional
  public void delete(long userId, long id) {
    ruleRepository.delete(requireOwned(userId, id));
  }

  /**
   * Re-run the user's rules over their uncategorized income/expense transactions, assigning the
   * matched category only when its kind matches the transaction type. Full scan — this is a batch
   * action, not a hot path.
   */
  @Transactional
  public ApplyRulesResponse apply(long userId) {
    List<MatchableRule> rules =
        ruleRepository.findByUserIdOrderByPriorityDescPatternAsc(userId).stream()
            .map(r -> new MatchableRule(r.getPattern(), r.getCategoryId(), r.getPriority()))
            .toList();
    List<Transaction> uncategorized =
        transactionRepository.findByUserIdAndDeletedAtIsNullAndCategoryIdIsNullAndTypeIn(
            userId, CATEGORIZABLE);
    if (rules.isEmpty() || uncategorized.isEmpty()) {
      return new ApplyRulesResponse(uncategorized.size(), 0);
    }

    Map<Long, CategoryKind> kindById = new HashMap<>();
    for (Category category : categoryRepository.findByUserIdOrderByNameAsc(userId)) {
      kindById.put(category.getId(), category.getKind());
    }

    List<Transaction> changed = new ArrayList<>();
    for (Transaction tx : uncategorized) {
      Long matched = RuleMatcher.match(tx.getDescription(), rules);
      if (matched != null && kindMatches(kindById.get(matched), tx.getType())) {
        tx.setCategoryId(matched);
        changed.add(tx);
      }
    }
    transactionRepository.saveAll(changed);
    return new ApplyRulesResponse(uncategorized.size(), changed.size());
  }

  private Rule requireOwned(long userId, long id) {
    return ruleRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> NotFoundException.of("Rule", id));
  }

  private void requireOwnedCategory(long userId, long categoryId) {
    categoryRepository
        .findByIdAndUserId(categoryId, userId)
        .orElseThrow(() -> NotFoundException.of("Category", categoryId));
  }

  private static boolean kindMatches(CategoryKind kind, TransactionType type) {
    if (kind == null) {
      return false;
    }
    return type == TransactionType.INCOME
        ? kind == CategoryKind.INCOME
        : kind == CategoryKind.EXPENSE;
  }

  private static void requireCurrentVersion(Rule rule, long expectedVersion) {
    if (rule.getVersion() == null || rule.getVersion() != expectedVersion) {
      throw new ConflictException("Rule was modified concurrently; reload and retry.");
    }
  }

  private static RuleResponse toResponse(Rule r) {
    return new RuleResponse(
        r.getId(), r.getPattern(), r.getCategoryId(), r.getPriority(), r.getVersion());
  }
}
