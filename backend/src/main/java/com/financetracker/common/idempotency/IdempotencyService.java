package com.financetracker.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.common.error.UnprocessableEntityException;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * At-most-once execution per {@code (user, scope, key)}. MUST be called inside the caller's
 * transaction so the claim, the created row and the stored response commit together — if the
 * operation fails, its claim rolls back with it, so a failed key is free to retry.
 */
@Service
public class IdempotencyService {

  private final IdempotencyKeyRepository repository;
  private final ObjectMapper objectMapper;

  public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public <T> T execute(
      long userId,
      String scope,
      String key,
      String fingerprint,
      Class<T> responseType,
      Supplier<T> operation) {
    if (key == null || key.isBlank()) {
      return operation.get();
    }
    if (repository.tryClaim(userId, scope, key, fingerprint) == 1) {
      T result = operation.get();
      repository.storeResponse(userId, scope, key, serialize(result));
      return result;
    }
    IdempotencyKey existing =
        repository
            .findByUserIdAndScopeAndKey(userId, scope, key)
            .orElseThrow(
                () -> new IllegalStateException("Idempotency row vanished after conflict"));
    if (!existing.getFingerprint().equals(fingerprint)) {
      throw new UnprocessableEntityException(
          "Idempotency-Key already used with a different request.");
    }
    return deserialize(existing.getResponseBody(), responseType);
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not store idempotent response", e);
    }
  }

  private <T> T deserialize(String body, Class<T> type) {
    try {
      return objectMapper.readValue(body, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not replay idempotent response", e);
    }
  }
}
