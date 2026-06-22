package com.financetracker.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/** Account kind. Persisted (and exposed over JSON) as the lowercase token the prototype uses. */
public enum AccountType {
  CHECKING("checking"),
  SAVINGS("savings"),
  CASH("cash"),
  CREDIT("credit");

  private final String value;

  AccountType(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static AccountType fromValue(String value) {
    return Arrays.stream(values())
        .filter(t -> t.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown account type: " + value));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<AccountType, String> {
    @Override
    public String convertToDatabaseColumn(AccountType attribute) {
      return attribute == null ? null : attribute.value();
    }

    @Override
    public AccountType convertToEntityAttribute(String dbData) {
      return dbData == null ? null : AccountType.fromValue(dbData);
    }
  }
}
