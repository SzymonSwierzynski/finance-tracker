package com.financetracker.transaction;

import com.financetracker.common.UserOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * A money movement in a single (native) currency. {@code amountMinor} is a positive BIGINT in minor
 * units; {@code rateToBase} (the only NUMERIC in the model) is locked at entry time so that
 * base-currency reports stay stable as live rates change (CLAUDE.md §7).
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
public class Transaction extends UserOwnedEntity {

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(name = "type", nullable = false)
  private TransactionType type;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "counter_account_id")
  private Long counterAccountId;

  @Column(name = "category_id")
  private Long categoryId;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Column(name = "rate_to_base", nullable = false)
  private BigDecimal rateToBase;

  @Column(name = "description", nullable = false)
  private String description = "";

  @Column(name = "note", nullable = false)
  private String note = "";

  @Column(name = "import_batch_id")
  private Long importBatchId;

  @Column(name = "dedupe_hash", nullable = false)
  private String dedupeHash;
}
