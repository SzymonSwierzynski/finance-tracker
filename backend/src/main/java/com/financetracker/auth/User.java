package com.financetracker.auth;

import com.financetracker.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A registered user. Minimal PII (email only); password is stored as a BCrypt hash. */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

  @Column(name = "email", nullable = false, unique = true, columnDefinition = "citext")
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "status", nullable = false)
  private UserStatus status = UserStatus.ACTIVE;
}
