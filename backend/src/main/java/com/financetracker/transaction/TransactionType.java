package com.financetracker.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/**
 * Transaction kind. Transfers move money between accounts and are neither income nor expense, so
 * they are excluded from spending reports. Persisted/exposed as the lowercase prototype token.
 */
public enum TransactionType {
  EXPENSE("expense"),
  INCOME("income"),
  TRANSFER("transfer");

  private final String value;

  TransactionType(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static TransactionType fromValue(String value) {
    return Arrays.stream(values())
        .filter(t -> t.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown transaction type: " + value));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<TransactionType, String> {
    @Override
    public String convertToDatabaseColumn(TransactionType attribute) {
      return attribute == null ? null : attribute.value();
    }

    @Override
    public TransactionType convertToEntityAttribute(String dbData) {
      return dbData == null ? null : TransactionType.fromValue(dbData);
    }
  }
}
