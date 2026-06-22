package com.financetracker.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base for every user-owned table. The {@code user_id} FK is the spine of data isolation: services
 * always scope queries by it and repositories expose only user-scoped finders, so no query can ever
 * return another user's row.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class UserOwnedEntity extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;
}
