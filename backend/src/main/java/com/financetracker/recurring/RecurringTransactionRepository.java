package com.financetracker.recurring;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Recurring-template persistence. Every finder is user-scoped. */
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

  List<RecurringTransaction> findByUserIdOrderByNextRunDateAsc(long userId);

  Optional<RecurringTransaction> findByIdAndUserId(long id, long userId);

  /** The user's active templates whose next run is due on or before {@code date}. */
  List<RecurringTransaction> findByUserIdAndActiveTrueAndNextRunDateLessThanEqual(
      long userId, LocalDate date);

  /** All active templates due on or before {@code date}, across users — for the scheduled sweep. */
  List<RecurringTransaction> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);
}
