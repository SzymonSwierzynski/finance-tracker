package com.financetracker.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Category persistence. Every finder is user-scoped; no unscoped lookup of user data. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

  List<Category> findByUserIdOrderByNameAsc(long userId);

  List<Category> findByUserIdAndKindOrderByNameAsc(long userId, CategoryKind kind);

  Optional<Category> findByIdAndUserId(long id, long userId);

  List<Category> findByParentId(long parentId);

  boolean existsByUserIdAndParentIdAndNameIgnoreCase(long userId, Long parentId, String name);

  long countByUserId(long userId);
}
