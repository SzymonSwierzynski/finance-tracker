package com.financetracker.category;

import com.financetracker.category.dto.CategoryResponse;
import com.financetracker.category.dto.CreateCategoryRequest;
import com.financetracker.category.dto.DeleteCategoryResponse;
import com.financetracker.category.dto.UpdateCategoryRequest;
import com.financetracker.common.error.ConflictException;
import com.financetracker.common.error.NotFoundException;
import com.financetracker.common.error.UnprocessableEntityException;
import com.financetracker.transaction.TransactionRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category business logic: ownership, the two-level rule (a subcategory's parent must be a
 * top-level category of the same kind), name uniqueness, and delete-uncategorizes (deleting a
 * category — and any subcategories — nulls the {@code categoryId} of affected transactions and
 * reports the count).
 */
@Service
public class CategoryService {

  /** Slate-400 — also the fixed Uncategorized colour in reports. */
  static final String FALLBACK_COLOR = "#94a3b8";

  private final CategoryRepository categoryRepository;
  private final TransactionRepository transactionRepository;

  public CategoryService(
      CategoryRepository categoryRepository, TransactionRepository transactionRepository) {
    this.categoryRepository = categoryRepository;
    this.transactionRepository = transactionRepository;
  }

  @Transactional
  public CategoryResponse create(long userId, CreateCategoryRequest request) {
    String name = request.name().trim();
    Long parentId = request.parentId();

    if (parentId != null) {
      Category parent = requireOwned(userId, parentId);
      if (parent.getParentId() != null) {
        throw new UnprocessableEntityException(
            "Categories are two levels only; the parent already has a parent.");
      }
      if (parent.getKind() != request.kind()) {
        throw new UnprocessableEntityException(
            "A subcategory must have the same kind as its parent.");
      }
    }

    if (categoryRepository.existsByUserIdAndParentIdAndNameIgnoreCase(userId, parentId, name)) {
      throw new ConflictException("A category with this name already exists here.");
    }

    Category category = new Category();
    category.setUserId(userId);
    category.setName(name);
    category.setKind(request.kind());
    category.setParentId(parentId);
    category.setColor(request.color() == null ? FALLBACK_COLOR : request.color());
    return toResponse(categoryRepository.save(category));
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> list(long userId, CategoryKind kind) {
    List<Category> categories =
        kind == null
            ? categoryRepository.findByUserIdOrderByNameAsc(userId)
            : categoryRepository.findByUserIdAndKindOrderByNameAsc(userId, kind);
    return categories.stream().map(CategoryService::toResponse).toList();
  }

  @Transactional
  public CategoryResponse update(long userId, long id, UpdateCategoryRequest request) {
    Category category = requireOwned(userId, id);
    requireCurrentVersion(category, request.version());
    if (request.name() != null) {
      String name = request.name().trim();
      if (!name.equalsIgnoreCase(category.getName())
          && categoryRepository.existsByUserIdAndParentIdAndNameIgnoreCase(
              userId, category.getParentId(), name)) {
        throw new ConflictException("A category with this name already exists here.");
      }
      category.setName(name);
    }
    if (request.color() != null) {
      category.setColor(request.color());
    }
    return toResponse(categoryRepository.saveAndFlush(category));
  }

  @Transactional
  public DeleteCategoryResponse delete(long userId, long id) {
    Category category = requireOwned(userId, id);

    // A parent cascade-deletes its subcategories; count transactions across all of them.
    List<Long> affectedIds = new ArrayList<>();
    affectedIds.add(category.getId());
    for (Category child : categoryRepository.findByParentId(category.getId())) {
      affectedIds.add(child.getId());
    }
    long uncategorized = transactionRepository.countByUserIdAndCategoryIdIn(userId, affectedIds);

    categoryRepository.delete(category); // cascade -> children; FK SET NULL -> transactions
    return new DeleteCategoryResponse(uncategorized);
  }

  private Category requireOwned(long userId, long id) {
    return categoryRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> NotFoundException.of("Category", id));
  }

  private static void requireCurrentVersion(Category category, long expectedVersion) {
    if (category.getVersion() == null || category.getVersion() != expectedVersion) {
      throw new ConflictException("Category was modified concurrently; reload and retry.");
    }
  }

  private static CategoryResponse toResponse(Category c) {
    return new CategoryResponse(
        c.getId(), c.getName(), c.getKind(), c.getParentId(), c.getColor(), c.getVersion());
  }
}
