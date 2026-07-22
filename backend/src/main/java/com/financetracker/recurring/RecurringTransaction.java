package com.financetracker.recurring;

import com.financetracker.common.UserOwnedEntity;
import com.financetracker.transaction.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * A recurring transaction template. It materializes into real {@code transactions} on its schedule:
 * whenever {@code nextRunDate} is due, a transaction is created and {@code nextRunDate} advances by
 * {@code frequency} × {@code intervalCount}, until {@code endDate} (if set). Only expense/income —
 * transfers are out of scope for v1.
 */
@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
public class RecurringTransaction extends UserOwnedEntity {

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "category_id")
  private Long categoryId;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(name = "type", nullable = false)
  private TransactionType type;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Column(name = "description", nullable = false)
  private String description = "";

  @Column(name = "note", nullable = false)
  private String note = "";

  @Column(name = "frequency", nullable = false)
  private RecurringFrequency frequency;

  @Column(name = "interval_count", nullable = false)
  private int intervalCount = 1;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "next_run_date", nullable = false)
  private LocalDate nextRunDate;

  @Column(name = "active", nullable = false)
  private boolean active = true;
}
