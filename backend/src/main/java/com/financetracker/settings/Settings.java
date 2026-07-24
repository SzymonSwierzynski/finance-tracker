package com.financetracker.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Per-user settings, 1:1 with the user (the primary key IS the user id). Because the id is the user
 * id, every lookup is inherently scoped to the owner.
 */
@Entity
@Table(name = "settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Settings {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "reporting_currency", nullable = false)
  private String reportingCurrency = "PLN";

  /**
   * When the default category tree was seeded for this user, or null if it never was. This is a
   * persisted fact rather than an inference from "the user has no categories": someone who deletes
   * every category has made a choice, and the seeder must not undo it on their next login.
   */
  @Column(name = "categories_seeded_at")
  private Instant categoriesSeededAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;
}
