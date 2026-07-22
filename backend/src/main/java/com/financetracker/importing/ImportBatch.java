package com.financetracker.importing;

import com.financetracker.common.UserOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One CSV import, grouping the transactions it created so the whole import can be undone. Deleting
 * the batch row cascades to those transactions (FK {@code ON DELETE CASCADE}).
 */
@Entity
@Table(name = "import_batches")
@Getter
@Setter
public class ImportBatch extends UserOwnedEntity {

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "row_count", nullable = false)
  private int count;
}
