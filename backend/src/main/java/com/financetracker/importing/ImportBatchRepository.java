package com.financetracker.importing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Import-batch persistence. User-scoped finders only. */
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

  /** The user's batches, newest first. */
  List<ImportBatch> findByUserIdOrderByCreatedAtDescIdDesc(long userId);

  Optional<ImportBatch> findByIdAndUserId(long id, long userId);
}
