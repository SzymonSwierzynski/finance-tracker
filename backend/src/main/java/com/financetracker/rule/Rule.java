package com.financetracker.rule;

import com.financetracker.common.UserOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An auto-categorization rule: when a transaction's description contains {@code pattern}
 * (case-insensitive substring), the transaction is assigned {@code categoryId}. Higher {@code
 * priority} wins; the first match in priority order applies (see {@link RuleMatcher}, ported from
 * the prototype's {@code matchCategory}).
 */
@Entity
@Table(name = "rules")
@Getter
@Setter
public class Rule extends UserOwnedEntity {

  @Column(name = "pattern", nullable = false)
  private String pattern;

  @Column(name = "category_id", nullable = false)
  private Long categoryId;

  @Column(name = "priority", nullable = false)
  private int priority;
}
