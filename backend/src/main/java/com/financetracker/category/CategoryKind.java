package com.financetracker.category;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/** Whether a category groups spending or earning. Persisted/exposed as the lowercase token. */
public enum CategoryKind {
  EXPENSE("expense"),
  INCOME("income");

  private final String value;

  CategoryKind(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static CategoryKind fromValue(String value) {
    return Arrays.stream(values())
        .filter(k -> k.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown category kind: " + value));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<CategoryKind, String> {
    @Override
    public String convertToDatabaseColumn(CategoryKind attribute) {
      return attribute == null ? null : attribute.value();
    }

    @Override
    public CategoryKind convertToEntityAttribute(String dbData) {
      return dbData == null ? null : CategoryKind.fromValue(dbData);
    }
  }
}
