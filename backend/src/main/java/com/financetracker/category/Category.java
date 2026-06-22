package com.financetracker.category;

import com.financetracker.common.UserOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A spending/earning category. Two levels only: a top-level category has {@code parentId == null};
 * a subcategory points at a top-level parent and may not itself be a parent (enforced in the
 * service).
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category extends UserOwnedEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "kind", nullable = false)
  private CategoryKind kind;

  @Column(name = "parent_id")
  private Long parentId;

  @Column(name = "color", nullable = false)
  private String color;
}
