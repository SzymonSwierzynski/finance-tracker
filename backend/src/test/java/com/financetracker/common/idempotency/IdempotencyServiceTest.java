package com.financetracker.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.common.error.UnprocessableEntityException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdempotencyServiceTest {

  record Result(String value) {}

  private final IdempotencyKeyRepository repo = mock(IdempotencyKeyRepository.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final IdempotencyService service = new IdempotencyService(repo, mapper);

  @Test
  void noKeyRunsOperationAndTouchesNothing() {
    AtomicInteger ran = new AtomicInteger();
    Result r =
        service.execute(
            1L,
            "transaction",
            null,
            "fp",
            Result.class,
            () -> {
              ran.incrementAndGet();
              return new Result("fresh");
            });
    assertThat(r.value()).isEqualTo("fresh");
    assertThat(ran.get()).isEqualTo(1);
    verifyNoInteractions(repo);
  }

  @Test
  void claimSucceeds_runsOperationAndStoresResponse() {
    when(repo.tryClaim(1L, "transaction", "k", "fp")).thenReturn(1);
    AtomicInteger ran = new AtomicInteger();
    Result r =
        service.execute(
            1L,
            "transaction",
            "k",
            "fp",
            Result.class,
            () -> {
              ran.incrementAndGet();
              return new Result("fresh");
            });
    assertThat(r.value()).isEqualTo("fresh");
    assertThat(ran.get()).isEqualTo(1);
    verify(repo).storeResponse(eq(1L), eq("transaction"), eq("k"), anyString());
  }

  @Test
  void conflictWithMatchingFingerprint_replaysWithoutRunning() throws Exception {
    when(repo.tryClaim(1L, "transaction", "k", "fp")).thenReturn(0);
    IdempotencyKey stored = new IdempotencyKey();
    stored.setFingerprint("fp");
    stored.setResponseBody(mapper.writeValueAsString(new Result("original")));
    when(repo.findByUserIdAndScopeAndKey(1L, "transaction", "k")).thenReturn(Optional.of(stored));

    AtomicInteger ran = new AtomicInteger();
    Result r =
        service.execute(
            1L,
            "transaction",
            "k",
            "fp",
            Result.class,
            () -> {
              ran.incrementAndGet();
              return new Result("should-not-run");
            });
    assertThat(r.value()).isEqualTo("original");
    assertThat(ran.get()).isZero();
    verify(repo, never()).storeResponse(anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  void conflictWithDifferentFingerprint_throws422() {
    when(repo.tryClaim(1L, "transaction", "k", "fp")).thenReturn(0);
    IdempotencyKey stored = new IdempotencyKey();
    stored.setFingerprint("OTHER");
    when(repo.findByUserIdAndScopeAndKey(1L, "transaction", "k")).thenReturn(Optional.of(stored));

    assertThatThrownBy(
            () ->
                service.execute(1L, "transaction", "k", "fp", Result.class, () -> new Result("x")))
        .isInstanceOf(UnprocessableEntityException.class);
  }
}
