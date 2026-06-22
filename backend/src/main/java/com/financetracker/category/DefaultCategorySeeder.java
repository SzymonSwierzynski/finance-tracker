package com.financetracker.category;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Idempotent default category tree for new users — ported from the Dexie prototype's {@code
 * seedDefaultCategories}. Only runs when the user has zero categories.
 */
@Component
public class DefaultCategorySeeder {

  private record SeedParent(String name, CategoryKind kind, String color, List<String> children) {}

  private static final List<SeedParent> DEFAULTS =
      List.of(
          new SeedParent(
              "Groceries", CategoryKind.EXPENSE, "#22c55e", List.of("Supermarket", "Convenience")),
          new SeedParent(
              "Eating out",
              CategoryKind.EXPENSE,
              "#f97316",
              List.of("Restaurants", "Coffee", "Takeaway")),
          new SeedParent(
              "Transport",
              CategoryKind.EXPENSE,
              "#3b82f6",
              List.of("Fuel", "Public transport", "Taxi")),
          new SeedParent(
              "Housing",
              CategoryKind.EXPENSE,
              "#8b5cf6",
              List.of("Rent", "Utilities", "Internet")),
          new SeedParent(
              "Health", CategoryKind.EXPENSE, "#ef4444", List.of("Pharmacy", "Doctor")),
          new SeedParent(
              "Entertainment",
              CategoryKind.EXPENSE,
              "#ec4899",
              List.of("Subscriptions", "Hobbies")),
          new SeedParent(
              "Shopping",
              CategoryKind.EXPENSE,
              "#eab308",
              List.of("Clothes", "Electronics")),
          new SeedParent("Salary", CategoryKind.INCOME, "#10b981", List.of()),
          new SeedParent("Other income", CategoryKind.INCOME, "#14b8a6", List.of()));

  private final CategoryRepository categoryRepository;

  public DefaultCategorySeeder(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  /** Seeds the default tree when {@code userId} has no categories yet. Safe to call repeatedly. */
  public void seedIfEmpty(long userId) {
    if (categoryRepository.countByUserId(userId) > 0) {
      return;
    }
    for (SeedParent parent : DEFAULTS) {
      Category created = new Category();
      created.setUserId(userId);
      created.setName(parent.name());
      created.setKind(parent.kind());
      created.setColor(parent.color());
      created = categoryRepository.save(created);

      for (String childName : parent.children()) {
        Category child = new Category();
        child.setUserId(userId);
        child.setName(childName);
        child.setKind(parent.kind());
        child.setParentId(created.getId());
        child.setColor(parent.color());
        categoryRepository.save(child);
      }
    }
  }
}
