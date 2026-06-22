package com.financetracker.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/** Account status. Persisted as the lowercase token the DB check constraint expects. */
public enum UserStatus {
  ACTIVE("active"),
  DISABLED("disabled");

  private final String value;

  UserStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static UserStatus fromValue(String value) {
    return Arrays.stream(values())
        .filter(s -> s.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown user status: " + value));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<UserStatus, String> {
    @Override
    public String convertToDatabaseColumn(UserStatus attribute) {
      return attribute == null ? null : attribute.value();
    }

    @Override
    public UserStatus convertToEntityAttribute(String dbData) {
      return dbData == null ? null : UserStatus.fromValue(dbData);
    }
  }
}
