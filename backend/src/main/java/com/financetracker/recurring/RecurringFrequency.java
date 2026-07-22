package com.financetracker.recurring;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.util.Arrays;

/** How often a recurring template repeats. Persisted/exposed as the lowercase token. */
public enum RecurringFrequency {
  DAILY("daily"),
  WEEKLY("weekly"),
  MONTHLY("monthly"),
  YEARLY("yearly");

  private final String value;

  RecurringFrequency(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static RecurringFrequency fromValue(String value) {
    return Arrays.stream(values())
        .filter(f -> f.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown frequency: " + value));
  }

  /**
   * Advance a date by {@code interval} periods of this frequency (month/year clamp to month-end).
   */
  public LocalDate advance(LocalDate date, int interval) {
    return switch (this) {
      case DAILY -> date.plusDays(interval);
      case WEEKLY -> date.plusWeeks(interval);
      case MONTHLY -> date.plusMonths(interval);
      case YEARLY -> date.plusYears(interval);
    };
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<RecurringFrequency, String> {
    @Override
    public String convertToDatabaseColumn(RecurringFrequency attribute) {
      return attribute == null ? null : attribute.value();
    }

    @Override
    public RecurringFrequency convertToEntityAttribute(String dbData) {
      return dbData == null ? null : RecurringFrequency.fromValue(dbData);
    }
  }
}
