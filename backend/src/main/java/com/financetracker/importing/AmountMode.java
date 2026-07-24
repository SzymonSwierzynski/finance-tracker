package com.financetracker.importing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/**
 * How a bank CSV encodes amount and direction: a single {@code signed} column (with {@code
 * expenseIsNegative}), or separate {@code debitCredit} columns. Persisted/exposed as the lowercase
 * prototype token.
 */
public enum AmountMode {
  SIGNED("signed"),
  DEBIT_CREDIT("debitCredit");

  private final String value;

  AmountMode(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static AmountMode fromValue(String value) {
    return Arrays.stream(values())
        .filter(m -> m.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown amount mode: " + value));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<AmountMode, String> {
    @Override
    public String convertToDatabaseColumn(AmountMode attribute) {
      return attribute == null ? null : attribute.value();
    }

    @Override
    public AmountMode convertToEntityAttribute(String dbData) {
      return dbData == null ? null : AmountMode.fromValue(dbData);
    }
  }
}
