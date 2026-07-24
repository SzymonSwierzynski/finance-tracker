package com.financetracker.budget;

import com.financetracker.common.UserOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A monthly spending limit for one expense category. {@code amountMinor} is BIGINT minor units in
 * the user's reporting (base) currency, so it compares directly against base-currency spend. At
 * most one budget per (user, category) — enforced by a unique constraint.
 */
@Entity
@Table(name = "budgets")
@Getter
@Setter
public class Budget extends UserOwnedEntity {

  @Column(name = "category_id", nullable = false)
  private Long categoryId;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;
}
