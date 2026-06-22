package com.financetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.config.PersistenceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Repository slice against real Postgres — confirms the scoped finders and citext behaviour. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
// Let Flyway own the schema (so email is really citext) and Hibernate only validate, as in prod —
// @DataJpaTest otherwise defaults ddl-auto to create-drop, which would recreate a plain-text
// column.
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(PersistenceConfig.class) // enable JPA auditing (not loaded by the slice) to fill timestamps
@Testcontainers
class UserRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private UserRepository users;

  @Test
  void findByEmailIsCaseInsensitive() {
    User user = new User();
    user.setEmail("Mixed@Example.com");
    user.setPasswordHash("hash");
    user.setStatus(UserStatus.ACTIVE);
    users.saveAndFlush(user);

    // citext column -> lookups ignore case.
    assertThat(users.findByEmail("mixed@example.com")).isPresent();
    assertThat(users.existsByEmail("MIXED@EXAMPLE.COM")).isTrue();
    assertThat(users.findByEmail("someone-else@example.com")).isEmpty();
  }
}
