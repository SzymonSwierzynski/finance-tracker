# Idempotency Keys Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `POST /transactions` and `POST /imports/commit` safe to retry — the same `Idempotency-Key` header replays the first result instead of creating a duplicate.

**Architecture:** A `common/idempotency` package: an `idempotency_keys` table (V14), an `IdempotencyService.execute(...)` that runs the operation inside the caller's existing `@Transactional` (claim via `INSERT … ON CONFLICT DO NOTHING`, run op, store the serialized response; on conflict replay or 422 on fingerprint mismatch), a nightly cleanup, and a frontend that sends a per-submit UUID.

**Tech Stack:** Spring Boot 3.5 / Java 21 / JPA / Flyway / Postgres 16; React 19 + TS. Build with **Java 21** (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`), absolute paths.

**Spec:** `docs/superpowers/specs/2026-07-27-idempotency-keys-design.md`

---

## Standing rules for the executor (project §17)

- **Commit only when the user asks; push only when the user asks.** The `Commit` steps are real, but **pause for the user's go-ahead** before each (backend then frontend, separate commits).
- **Backend first, then frontend.** Keep both green: `cd backend && ./gradlew build` and `cd frontend && npm run lint && npm test && npm run build`.
- **Stop at the phase boundary** (after Task 6) for in-app testing.
- Local-only docs (`HANDOFF.md`, `CLAUDE.md`, `review.md`) are git-ignored — update on disk, never commit.

---

## Task 0: Branch

- [ ] **Step 1: Create the branch off `main`**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod
git checkout main && git switch -c backlog-idempotency-keys
```
Expected: `Switched to a new branch 'backlog-idempotency-keys'`.

---

## Task 1: Persistence + fingerprints

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__idempotency_keys.sql`
- Create: `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyKey.java`
- Create: `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyKeyRepository.java`
- Create: `backend/src/main/java/com/financetracker/common/idempotency/Fingerprints.java`
- Test: `backend/src/test/java/com/financetracker/common/idempotency/FingerprintsTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/financetracker/common/idempotency/FingerprintsTest.java`:
```java
package com.financetracker.common.idempotency;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Deterministic, collision-resistant request fingerprints. */
class FingerprintsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  record Sample(String a, int b) {}

  @Test
  void sameInputSameHex_differentInputDifferentHex() {
    String h1 = Fingerprints.sha256Hex("hello".getBytes(UTF_8));
    String h2 = Fingerprints.sha256Hex("hello".getBytes(UTF_8));
    String h3 = Fingerprints.sha256Hex("world".getBytes(UTF_8));
    assertThat(h1).isEqualTo(h2).hasSize(64); // SHA-256 hex
    assertThat(h1).isNotEqualTo(h3);
  }

  @Test
  void ofSerializesValueAndIsStable() {
    String a = Fingerprints.of(mapper, new Sample("x", 1));
    String b = Fingerprints.of(mapper, new Sample("x", 1));
    String c = Fingerprints.of(mapper, new Sample("x", 2));
    assertThat(a).isEqualTo(b);
    assertThat(a).isNotEqualTo(c);
  }

  @Test
  void extraPartsAreLengthDelimitedSoConcatenationCannotCollide() {
    String ab = Fingerprints.sha256Hex("ab".getBytes(UTF_8), "c".getBytes(UTF_8));
    String aBc = Fingerprints.sha256Hex("a".getBytes(UTF_8), "bc".getBytes(UTF_8));
    assertThat(ab).isNotEqualTo(aBc);
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.common.idempotency.FingerprintsTest'
```
Expected: FAIL — `Fingerprints` does not exist (compile error).

- [ ] **Step 3: Create the migration**

Create `backend/src/main/resources/db/migration/V14__idempotency_keys.sql`:
```sql
-- Backlog B: idempotency keys for POST /transactions and POST /imports/commit. A client that sends
-- the same Idempotency-Key twice gets the first response replayed instead of a duplicate. Scoped per
-- (user, endpoint); the fingerprint guards against reusing a key for a different request.

CREATE TABLE idempotency_keys (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope               TEXT        NOT NULL,
    idempotency_key     TEXT        NOT NULL,
    request_fingerprint TEXT        NOT NULL,
    response_body       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_keys_uq UNIQUE (user_id, scope, idempotency_key)
);
CREATE INDEX idx_idempotency_keys_created ON idempotency_keys (created_at);
```

- [ ] **Step 4: Create the entity**

Create `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyKey.java`:
```java
package com.financetracker.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A claimed idempotency key. Standalone (not {@code UserOwnedEntity}) — write-once, no optimistic
 * lock or updated_at needed. {@code responseBody} is the JSON of the first successful response,
 * replayed verbatim on a repeat with a matching fingerprint.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
public class IdempotencyKey {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private String scope;

  @Column(name = "idempotency_key", nullable = false)
  private String key;

  @Column(name = "request_fingerprint", nullable = false)
  private String fingerprint;

  @Column(name = "response_body")
  private String responseBody;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;
}
```

- [ ] **Step 5: Create the repository**

Create `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyKeyRepository.java`:
```java
package com.financetracker.common.idempotency;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

  /**
   * Claim the key if free. {@code ON CONFLICT DO NOTHING} does NOT abort the transaction (a raw
   * unique violation would), so the caller can safely read the existing row afterward. A concurrent
   * duplicate blocks on the unique index until the first tx commits, then gets 0 rows here. Returns
   * rows inserted: 1 = claimed, 0 = already present.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO idempotency_keys (user_id, scope, idempotency_key, request_fingerprint)
          VALUES (:userId, :scope, :key, :fingerprint)
          ON CONFLICT (user_id, scope, idempotency_key) DO NOTHING
          """,
      nativeQuery = true)
  int tryClaim(
      @Param("userId") long userId,
      @Param("scope") String scope,
      @Param("key") String key,
      @Param("fingerprint") String fingerprint);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE idempotency_keys SET response_body = :body
          WHERE user_id = :userId AND scope = :scope AND idempotency_key = :key
          """,
      nativeQuery = true)
  int storeResponse(
      @Param("userId") long userId,
      @Param("scope") String scope,
      @Param("key") String key,
      @Param("body") String body);

  Optional<IdempotencyKey> findByUserIdAndScopeAndKey(long userId, String scope, String key);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :cutoff")
  int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
```

- [ ] **Step 6: Create the fingerprint helper**

Create `backend/src/main/java/com/financetracker/common/idempotency/Fingerprints.java`:
```java
package com.financetracker.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 request fingerprints. Parts are length-prefixed so concatenations cannot collide. */
public final class Fingerprints {

  private Fingerprints() {}

  public static String sha256Hex(byte[]... parts) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      for (byte[] p : parts) {
        md.update(ByteBuffer.allocate(4).putInt(p.length).array());
        md.update(p);
      }
      return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** Fingerprint the JSON of {@code value} plus any extra byte parts (e.g. an uploaded file). */
  public static String of(ObjectMapper mapper, Object value, byte[]... extra) {
    try {
      byte[][] parts = new byte[extra.length + 1][];
      parts[0] = mapper.writeValueAsBytes(value);
      System.arraycopy(extra, 0, parts, 1, extra.length);
      return sha256Hex(parts);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not fingerprint request", e);
    }
  }
}
```

- [ ] **Step 7: Run the test to confirm it passes**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.common.idempotency.FingerprintsTest'
```
Expected: PASS (3 tests).

- [ ] **Step 8: Commit** (after the user's go-ahead)

```bash
git add backend/src/main/resources/db/migration/V14__idempotency_keys.sql \
        backend/src/main/java/com/financetracker/common/idempotency/ \
        backend/src/test/java/com/financetracker/common/idempotency/FingerprintsTest.java
git commit -m "feat(backend): idempotency persistence + fingerprints (V14)"
```

---

## Task 2: IdempotencyService + properties + cleanup

**Files:**
- Create: `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyService.java`
- Create: `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyProperties.java`
- Create: `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyKeyCleanup.java`
- Test: `backend/src/test/java/com/financetracker/common/idempotency/IdempotencyServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/financetracker/common/idempotency/IdempotencyServiceTest.java`:
```java
package com.financetracker.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        service.execute(1L, "transaction", null, "fp", Result.class, () -> {
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
        service.execute(1L, "transaction", "k", "fp", Result.class, () -> {
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
        service.execute(1L, "transaction", "k", "fp", Result.class, () -> {
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
                service.execute(
                    1L, "transaction", "k", "fp", Result.class, () -> new Result("x")))
        .isInstanceOf(UnprocessableEntityException.class);
  }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.common.idempotency.IdempotencyServiceTest'
```
Expected: FAIL — `IdempotencyService` does not exist.

- [ ] **Step 3: Create `IdempotencyService`**

Create `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyService.java`:
```java
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
            .orElseThrow(() -> new IllegalStateException("Idempotency row vanished after conflict"));
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
```

- [ ] **Step 4: Run the test to confirm it passes**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.common.idempotency.IdempotencyServiceTest'
```
Expected: PASS (4 tests).

- [ ] **Step 5: Create the properties + cleanup** (no separate test — exercised by the integration test in Task 4)

Create `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyProperties.java`:
```java
package com.financetracker.common.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Retention window + purge cron. Picked up by {@code @ConfigurationPropertiesScan} on Application. */
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(
    @DefaultValue("48h") Duration retention, @DefaultValue("0 30 3 * * *") String cleanupCron) {}
```

Create `backend/src/main/java/com/financetracker/common/idempotency/IdempotencyKeyCleanup.java`:
```java
package com.financetracker.common.idempotency;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly purge of idempotency keys older than the retention window (mirrors {@code
 * RefreshTokenCleanup}). The delete is idempotent, so running it on every scaled-out instance is
 * harmless.
 */
@Component
public class IdempotencyKeyCleanup {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanup.class);

  private final IdempotencyKeyRepository repository;
  private final IdempotencyProperties properties;

  public IdempotencyKeyCleanup(
      IdempotencyKeyRepository repository, IdempotencyProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  @Scheduled(cron = "${app.idempotency.cleanup-cron:0 30 3 * * *}")
  @Transactional
  public void purgeOld() {
    int deleted = repository.deleteCreatedBefore(Instant.now().minus(properties.retention()));
    if (deleted > 0) {
      log.info("Purged {} old idempotency key(s)", deleted);
    }
  }
}
```

- [ ] **Step 6: Commit** (after the user's go-ahead; may be batched with Task 1)

```bash
git add backend/src/main/java/com/financetracker/common/idempotency/ \
        backend/src/test/java/com/financetracker/common/idempotency/IdempotencyServiceTest.java
git commit -m "feat(backend): idempotency service + retention cleanup"
```

---

## Task 3: Wire both endpoints

Wrap each operation in `idempotencyService.execute(...)` inside its existing `@Transactional`, reading the header in the controller. The only callers are the two controllers (verified), so the signatures change directly.

**Files:**
- Modify: `backend/src/main/java/com/financetracker/transaction/TransactionController.java`
- Modify: `backend/src/main/java/com/financetracker/transaction/TransactionService.java`
- Modify: `backend/src/main/java/com/financetracker/importing/ImportController.java`
- Modify: `backend/src/main/java/com/financetracker/importing/ImportService.java`

- [ ] **Step 1: Transactions controller — read the header, pass it down**

In `TransactionController.java`, add the import `import org.springframework.web.bind.annotation.RequestHeader;` and replace the `create` method:
```java
  @PostMapping
  public ResponseEntity<TransactionResponse> create(
      @CurrentUser AuthUser user,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateTransactionRequest request) {
    TransactionResponse created = transactionService.create(user.id(), request, idempotencyKey);
    return ResponseEntity.created(URI.create("/api/v1/transactions/" + created.id())).body(created);
  }
```

- [ ] **Step 2: TransactionService — inject deps, wrap the body**

In `TransactionService.java`:

Add imports:
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.common.idempotency.Fingerprints;
import com.financetracker.common.idempotency.IdempotencyService;
```

Add two constructor params + fields (append `idempotencyService`, `objectMapper` to the existing constructor):
```java
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;

  public TransactionService(
      TransactionRepository transactionRepository,
      AccountRepository accountRepository,
      CategoryRepository categoryRepository,
      RateResolver rateResolver,
      MeterRegistry meterRegistry,
      IdempotencyService idempotencyService,
      ObjectMapper objectMapper) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.categoryRepository = categoryRepository;
    this.rateResolver = rateResolver;
    this.idempotencyService = idempotencyService;
    this.objectMapper = objectMapper;
    // "entered" not "created": Prometheus treats a _created suffix as reserved and would drop it.
    this.transactionsCreated =
        Counter.builder("financetracker.transactions.entered")
            .description("Manually-entered transactions created")
            .register(meterRegistry);
  }
```

Change `create(...)` to delegate through idempotency, and rename the current body to `createInternal`:
```java
  @Transactional
  public TransactionResponse create(
      long userId, CreateTransactionRequest request, String idempotencyKey) {
    return idempotencyService.execute(
        userId,
        "transaction",
        idempotencyKey,
        Fingerprints.of(objectMapper, request),
        TransactionResponse.class,
        () -> createInternal(userId, request));
  }

  private TransactionResponse createInternal(long userId, CreateTransactionRequest request) {
    Account account = requireOwnedAccount(userId, request.accountId());
    // ... the rest of the current create() body, unchanged, through:
    TransactionResponse response = toResponse(transactionRepository.save(tx));
    transactionsCreated.increment();
    return response;
  }
```
(Only the method signature/wrapper changes; the body lines that build and save the `Transaction` are moved verbatim into `createInternal`. `createInternal` is private and runs within the outer `@Transactional`, so the meter increment only fires on a real create — never on a replay.)

- [ ] **Step 3: Import controller — read the header**

In `ImportController.java`, add `import org.springframework.web.bind.annotation.RequestHeader;` and replace `commit`:
```java
  @PostMapping(path = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CommitResponse> commit(
      @CurrentUser AuthUser user,
      @RequestParam long accountId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestPart("file") MultipartFile file,
      @RequestPart("mapping") @Valid ImportMapping mapping) {
    String name = file.getOriginalFilename() == null ? "import.csv" : file.getOriginalFilename();
    CommitResponse result =
        importService.commit(user.id(), accountId, name, bytes(file), mapping, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }
```

- [ ] **Step 4: ImportService — inject deps, wrap commit**

In `ImportService.java`:

Add imports:
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.common.idempotency.Fingerprints;
import com.financetracker.common.idempotency.IdempotencyService;
import java.nio.charset.StandardCharsets;
```

Add two constructor params + fields (append to the existing constructor):
```java
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;
```
(add `IdempotencyService idempotencyService, ObjectMapper objectMapper` to the constructor signature and assign both).

Change `commit(...)` to delegate, renaming the current body to `commitInternal`:
```java
  @Transactional
  public CommitResponse commit(
      long userId,
      long accountId,
      String fileName,
      byte[] file,
      ImportMapping mapping,
      String idempotencyKey) {
    String fingerprint =
        Fingerprints.of(
            objectMapper, mapping, Long.toString(accountId).getBytes(StandardCharsets.UTF_8), file);
    return idempotencyService.execute(
        userId,
        "import-commit",
        idempotencyKey,
        fingerprint,
        CommitResponse.class,
        () -> commitInternal(userId, accountId, fileName, file, mapping));
  }

  private CommitResponse commitInternal(
      long userId, long accountId, String fileName, byte[] file, ImportMapping mapping) {
    Account account = requireOwnedAccount(userId, accountId);
    // ... the rest of the current commit() body, unchanged, through both returns.
  }
```

- [ ] **Step 5: Compile check**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL. (Full verification is Task 4.)

---

## Task 4: Integration test + full backend build

**Files:**
- Test: `backend/src/test/java/com/financetracker/common/idempotency/IdempotencyIsolationTest.java`

- [ ] **Step 1: Write the integration test**

Create `backend/src/test/java/com/financetracker/common/idempotency/IdempotencyIsolationTest.java`:
```java
package com.financetracker.common.idempotency;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Idempotency-Key on POST /transactions and POST /imports/commit: replay vs duplicate, strict
 * fingerprint mismatch, per-user isolation, and retention purge. Shared-DB pitfall (§13): unique
 * users, per-user assertions.
 */
class IdempotencyIsolationTest extends AbstractIntegrationTest {

  @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;

  private static final String TX =
      "{\"date\":\"2026-05-01\",\"amountMinor\":1999,\"type\":\"expense\",\"accountId\":%d}";

  @Test
  void sameKeyReplaysTheTransactionInsteadOfDuplicating() throws Exception {
    RegisteredUser user = register("idem-tx@example.com", "password123");
    long account = createAccount(user);
    String body = String.format(TX, account);

    MvcResult first =
        mockMvc
            .perform(postTx(user, body, "key-1"))
            .andExpect(status().isCreated())
            .andReturn();
    long firstId = id(first);

    // Same key + same body -> replay: identical id, no second row.
    mockMvc
        .perform(postTx(user, body, "key-1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(firstId));
    assertThat(transactionCount(user)).isEqualTo(1);
  }

  @Test
  void sameKeyDifferentBodyIsRejected() throws Exception {
    RegisteredUser user = register("idem-mismatch@example.com", "password123");
    long account = createAccount(user);
    mockMvc.perform(postTx(user, String.format(TX, account), "key-2")).andExpect(status().isCreated());

    String different =
        "{\"date\":\"2026-05-01\",\"amountMinor\":9999,\"type\":\"expense\",\"accountId\":" + account + "}";
    mockMvc
        .perform(postTx(user, different, "key-2"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void withoutKeyDuplicatesAsBefore() throws Exception {
    RegisteredUser user = register("idem-nokey@example.com", "password123");
    long account = createAccount(user);
    String body = String.format(TX, account);
    mockMvc.perform(postTx(user, body, null)).andExpect(status().isCreated());
    mockMvc.perform(postTx(user, body, null)).andExpect(status().isCreated());
    assertThat(transactionCount(user)).isEqualTo(2);
  }

  @Test
  void keysAreScopedPerUser() throws Exception {
    RegisteredUser alice = register("idem-a@example.com", "password123");
    RegisteredUser bob = register("idem-b@example.com", "password123");
    long aliceAcct = createAccount(alice);
    long bobAcct = createAccount(bob);
    // Same key string for both users is independent — Bob is not blocked or replayed by Alice's key.
    mockMvc.perform(postTx(alice, String.format(TX, aliceAcct), "shared")).andExpect(status().isCreated());
    mockMvc.perform(postTx(bob, String.format(TX, bobAcct), "shared")).andExpect(status().isCreated());
    assertThat(transactionCount(alice)).isEqualTo(1);
    assertThat(transactionCount(bob)).isEqualTo(1);
  }

  @Test
  void importCommitReplaysInsteadOfReExecuting() throws Exception {
    RegisteredUser user = register("idem-import@example.com", "password123");
    long account = createAccount(user);

    mockMvc
        .perform(commit(user, account, "key-imp"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(2));
    // Replay: same body echoed (imported=2), NOT the dedupe path (which would report imported=0).
    mockMvc
        .perform(commit(user, account, "key-imp"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(2));
    // One batch, two transactions — the second commit created nothing.
    mockMvc
        .perform(get("/api/v1/imports/batches").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    assertThat(transactionCount(user)).isEqualTo(2);
  }

  @Test
  void purgedKeysNoLongerReplay() throws Exception {
    RegisteredUser user = register("idem-purge@example.com", "password123");
    long account = createAccount(user);
    String body = String.format(TX, account);
    long firstId = id(mockMvc.perform(postTx(user, body, "key-purge")).andReturn());

    // Purge removes the key row; the same key now creates a fresh transaction instead of replaying.
    idempotencyKeyRepository.deleteCreatedBefore(Instant.now().plusSeconds(5));
    long secondId = id(mockMvc.perform(postTx(user, body, "key-purge")).andReturn());
    assertThat(secondId).isNotEqualTo(firstId);
  }

  // --- helpers ---

  private MockHttpServletRequestBuilder postTx(RegisteredUser user, String body, String key) {
    MockHttpServletRequestBuilder b =
        post("/api/v1/transactions")
            .header(HttpHeaders.AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    return key == null ? b : b.header("Idempotency-Key", key);
  }

  private MockHttpServletRequestBuilder commit(RegisteredUser user, long account, String key) {
    String csv = "Date;Title;Amount\n15.05.2026;Shop;-19,99\n16.05.2026;Pay;5 000,00\n";
    String mapping =
        "{\"delimiter\":\";\",\"encoding\":\"utf-8\",\"hasHeader\":true,\"dateIndex\":0,"
            + "\"dateFormat\":\"auto\",\"descriptionIndex\":1,\"amountMode\":\"signed\","
            + "\"amountIndex\":2,\"expenseIsNegative\":true,\"debitIndex\":-1,\"creditIndex\":-1}";
    return multipart("/api/v1/imports/commit")
        .file(new MockMultipartFile("file", "may.csv", "text/csv", csv.getBytes(UTF_8)))
        .file(new MockMultipartFile("mapping", "", "application/json", mapping.getBytes(UTF_8)))
        .param("accountId", String.valueOf(account))
        .header(HttpHeaders.AUTHORIZATION, bearer(user))
        .header("Idempotency-Key", key);
  }

  private long createAccount(RegisteredUser user) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Checking\",\"type\":\"checking\",\"currency\":\"PLN\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private int transactionCount(RegisteredUser user) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/transactions?size=200").header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("items").size();
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
```

- [ ] **Step 2: Run the integration test**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.financetracker.common.idempotency.IdempotencyIsolationTest'
```
Expected: PASS (6 tests). (Docker must be running for Testcontainers.)

- [ ] **Step 3: Full backend gate**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build
```
Expected: BUILD SUCCESSFUL (Spotless + all tests + JaCoCo ≥ 0.85). If Spotless flags formatting, run `./gradlew spotlessApply` and rebuild.

- [ ] **Step 4: Commit** (after the user's go-ahead)

```bash
git add backend/src/main/java/com/financetracker/transaction/ \
        backend/src/main/java/com/financetracker/importing/ \
        backend/src/test/java/com/financetracker/common/idempotency/IdempotencyIsolationTest.java
git commit -m "feat(backend): idempotency keys on transaction + import-commit"
```

---

## Task 5: Frontend — send a per-submit key

**Files:**
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/features/transactions/api.ts`
- Modify: `frontend/src/features/transactions/hooks.ts`
- Modify: `frontend/src/features/import/api.ts`
- Modify: `frontend/src/features/import/hooks.ts`
- Test: `frontend/src/api/client.idempotency.test.ts`

- [ ] **Step 1: `client.ts` — accept and send the header**

In `RequestOptions` (around line 43), add:
```ts
  /** Sent as the `Idempotency-Key` header; a retry of the same submit reuses it to avoid duplicates. */
  idempotencyKey?: string
```
In `send()` (around line 125), after the Authorization line, add:
```ts
    if (opts.idempotencyKey) finalHeaders['Idempotency-Key'] = opts.idempotencyKey
```

- [ ] **Step 2: Transactions — generate a key per create**

In `frontend/src/features/transactions/api.ts`, change `create`:
```ts
  create: (body: CreateTransactionRequest, idempotencyKey?: string) =>
    api.post<Transaction>('/api/v1/transactions', body, { idempotencyKey }),
```
In `frontend/src/features/transactions/hooks.ts`, change the `useCreateTransaction` mutationFn:
```ts
    mutationFn: (body: CreateTransactionRequest) =>
      transactionsApi.create(body, crypto.randomUUID()),
```

- [ ] **Step 3: Import — generate a key per commit**

In `frontend/src/features/import/api.ts`, change `commit`:
```ts
  commit: (accountId: number, file: File, mapping: ImportMapping, idempotencyKey?: string) =>
    api.post<CommitResult>('/api/v1/imports/commit', form(file, mapping), {
      params: { accountId },
      idempotencyKey,
    }),
```
In `frontend/src/features/import/hooks.ts`, change the `useCommitImport` mutationFn:
```ts
    mutationFn: ({ accountId, file, mapping }: ImportArgs) =>
      importApi.commit(accountId, file, mapping, crypto.randomUUID()),
```

- [ ] **Step 4: Write the header unit test**

Create `frontend/src/api/client.idempotency.test.ts`:
```ts
import { afterEach, expect, it, vi } from 'vitest'
import { api } from './client'

afterEach(() => vi.restoreAllMocks())

it('sends the Idempotency-Key header when provided', async () => {
  const fetchMock = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response('{"ok":true}', { status: 201 }))

  await api.post('/api/v1/transactions', { amountMinor: 1 }, { idempotencyKey: 'abc-123' })

  const headers = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>
  expect(headers['Idempotency-Key']).toBe('abc-123')
})

it('omits the header when no key is given', async () => {
  const fetchMock = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response('{"ok":true}', { status: 201 }))

  await api.post('/api/v1/transactions', { amountMinor: 1 })

  const headers = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>
  expect(headers['Idempotency-Key']).toBeUndefined()
})
```

- [ ] **Step 5: Run the frontend gate**

Run:
```bash
cd /Users/szymonswierzynski/Downloads/Praca/Programowanie/finance-tracker-prod/frontend
npm run lint && npm test && npm run build
```
Expected: eslint clean, Vitest green (existing + 2 new), `tsc -b` + vite build succeed.

- [ ] **Step 6: Commit** (after the user's go-ahead)

```bash
git add frontend/src/api/client.ts frontend/src/api/client.idempotency.test.ts \
        frontend/src/features/transactions/ frontend/src/features/import/
git commit -m "feat(frontend): send Idempotency-Key on transaction + import commit"
```

---

## Task 6: Boundary — verify + document

- [ ] **Step 1: One-off manual/Playwright check (throwaway, not committed)**

With the stack up, submit a transaction twice with the same key via `curl` (or double-submit in the UI) and confirm one row + an identical body; a different body on the same key → 422. Don't add a committed E2E spec.

- [ ] **Step 2: Update `HANDOFF.md` + `CLAUDE.md`** (local-only, never committed)

Record backlog item **B (idempotency keys)** delivered: V14 table, `common/idempotency` package, both POSTs wrapped, 48h retention purge, frontend sends a per-submit UUID. Migration table → V14; **next free migration V15**; next backlog item **C (soft-delete/undo)**. Update the §17 program checklist.

- [ ] **Step 3: Stop for the user to test in-app.** Report green builds with output; push only when asked.

---

## Self-review notes (author)

- **Spec coverage:** optional header + two endpoints (Tasks 3) · strict fingerprint 422 (Task 2 unit + Task 4) · replay (Task 4) · atomic claim/ON CONFLICT (Task 1 repo + Task 2 service) · per-(user,scope) isolation (Task 4) · retention purge (Tasks 2 + 4) · frontend keys (Task 5) · V14 (Task 1). All §-sections mapped.
- **Placeholder scan:** the two "…rest of the current body, unchanged" notes in Task 3 are deliberate move-verbatim instructions (the full bodies already exist in the files and are shown in the spec/context), not missing code — each names the exact anchor lines to preserve.
- **Type consistency:** `execute(userId, scope, key, fingerprint, Class<T>, Supplier<T>)`, `Fingerprints.of(mapper, value, byte[]...)`, `tryClaim/storeResponse/findByUserIdAndScopeAndKey/deleteCreatedBefore`, and scopes `"transaction"`/`"import-commit"` are used identically across tasks.
- **Back-compat:** new field on `RequestOptions` is optional; no header → `execute` returns `operation.get()` unchanged; both service signatures' only callers (the two controllers) are updated in the same task.
