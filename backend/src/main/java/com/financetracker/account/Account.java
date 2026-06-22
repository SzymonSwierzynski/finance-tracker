package com.financetracker.account;

import com.financetracker.common.UserOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A user's account (checking, savings, cash, credit). Money is BIGINT minor units; {@code
 * startingBalanceMinor} is only meaningful when {@code trackBalance} is true.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account extends UserOwnedEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "type", nullable = false)
  private AccountType type;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Column(name = "starting_balance_minor")
  private Long startingBalanceMinor;

  @Column(name = "track_balance", nullable = false)
  private boolean trackBalance = false;

  @Column(name = "archived", nullable = false)
  private boolean archived = false;
}
