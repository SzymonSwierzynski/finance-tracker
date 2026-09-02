package com.financetracker.importing;

import com.financetracker.common.UserOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The remembered CSV column mapping for one (user, account) — see {@code ImportMapping}. */
@Entity
@Table(name = "import_profiles")
@Getter
@Setter
public class ImportProfile extends UserOwnedEntity {

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "delimiter")
  private String delimiter;

  @Column(name = "encoding")
  private String encoding;

  @Column(name = "has_header", nullable = false)
  private boolean hasHeader;

  @Column(name = "date_index", nullable = false)
  private int dateIndex;

  @Column(name = "date_format", nullable = false)
  private String dateFormat;

  @Column(name = "description_index", nullable = false)
  private int descriptionIndex;

  @Column(name = "amount_mode", nullable = false)
  private AmountMode amountMode;

  @Column(name = "amount_index", nullable = false)
  private int amountIndex;

  @Column(name = "expense_is_negative", nullable = false)
  private boolean expenseIsNegative;

  @Column(name = "debit_index", nullable = false)
  private int debitIndex;

  @Column(name = "credit_index", nullable = false)
  private int creditIndex;

  @Column(name = "header_row_index")
  private Integer headerRowIndex;

  @Column(name = "description_indexes")
  private String descriptionIndexes; // comma-joined, e.g. "2,3"; null = use descriptionIndex
}
