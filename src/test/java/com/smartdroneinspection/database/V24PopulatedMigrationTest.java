package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class V24PopulatedMigrationTest {

  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbcTemplate;

  private String schema;

  @AfterEach
  void dropSchema() {
    if (schema != null) {
      jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }

  @Test
  void migratesDeterministicallyMappedCustomerRolesOnPopulatedV23Schema() {
    migrateThroughV23();
    UUID organizationId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();

    jdbcTemplate.update(
        "INSERT INTO " + schema + ".organizations (id, name, code) VALUES (?, ?, ?)",
        organizationId,
        "Customer",
        "ORG-" + organizationId);
    insertUser(clientId, "CUSTOMER_ORGANIZATION", organizationId);
    insertUser(inspectorId, "CUSTOMER_ORGANIZATION", organizationId);
    jdbcTemplate.update(
        "INSERT INTO " + schema + ".user_roles (id, user_id, role) VALUES (?, ?, ?)",
        UUID.randomUUID(),
        clientId,
        "CLIENT");
    jdbcTemplate.update(
        "INSERT INTO " + schema + ".user_roles (id, user_id, role) VALUES (?, ?, ?)",
        UUID.randomUUID(),
        inspectorId,
        "INSPECTOR");

    Flyway configured = configureFlyway(schema, null);
    configured.migrate();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT role FROM " + schema + ".user_roles WHERE user_id = ?",
                String.class,
                clientId))
        .isEqualTo("ORG_ADMIN");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT role FROM " + schema + ".user_roles WHERE user_id = ?",
                String.class,
                inspectorId))
        .isEqualTo("INSPECTOR");
  }

  @Test
  void refusesProviderIdentityInsteadOfInventingCustomerOrganizationScope() {
    migrateThroughV23();
    UUID providerId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO "
            + schema
            + ".provider_organizations "
            + "(id, name, legal_name, tax_code, business_license_no, status) "
            + "VALUES (?, 'Provider', 'Provider Ltd', ?, 'LIC-1', 'VERIFIED')",
        providerId,
        "TAX-" + providerId.toString().substring(0, 20));
    insertUser(userId, "SERVICE_WORKFORCE", null);
    jdbcTemplate.update(
        "UPDATE " + schema + ".users SET provider_id = ? WHERE id = ?", providerId, userId);
    jdbcTemplate.update(
        "INSERT INTO " + schema + ".user_roles (id, user_id, role) VALUES (?, ?, ?)",
        UUID.randomUUID(),
        userId,
        "PROVIDER_MANAGER");

    Flyway configured = configureFlyway(schema, null);

    assertThatThrownBy(configured::migrate)
        .satisfies(
            failure ->
                assertThat(causalText(failure))
                    .contains("V24 precheck failed")
                    .contains(userId.toString()));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT provider_id FROM " + schema + ".users WHERE id = ?", UUID.class, userId))
        .isEqualTo(providerId);
  }

  @Test
  void refusesLegacyPlatformOperatorInsteadOfGrantingFullAdmin() {
    migrateThroughV23();
    UUID userId = UUID.randomUUID();
    insertUser(userId, "PLATFORM", null);
    jdbcTemplate.update(
        "INSERT INTO " + schema + ".user_roles (id, user_id, role) VALUES (?, ?, ?)",
        UUID.randomUUID(),
        userId,
        "PLATFORM_OPERATOR");

    Flyway configured = configureFlyway(schema, null);

    // A marketplace operator had commercial/vetting duties, not platform user administration.
    // Collapsing it into ADMIN would silently grant user management, so the migration must stop.
    assertThatThrownBy(configured::migrate)
        .satisfies(
            failure ->
                assertThat(causalText(failure))
                    .contains("V24 precheck failed")
                    .contains(userId.toString()));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT role FROM " + schema + ".user_roles WHERE user_id = ?",
                String.class,
                userId))
        .isEqualTo("PLATFORM_OPERATOR");
  }

  private String causalText(Throwable failure) {
    StringBuilder text = new StringBuilder();
    Throwable cause = failure;
    while (cause != null) {
      text.append(cause).append('\n');
      cause = cause.getCause();
    }
    return text.toString();
  }

  private Flyway migrateThroughV23() {
    schema = "migration_v24_" + UUID.randomUUID().toString().replace("-", "");
    Flyway flyway = configureFlyway(schema, MigrationVersion.fromVersion("23"));
    flyway.migrate();
    return flyway;
  }

  private Flyway configureFlyway(String targetSchema, MigrationVersion target) {
    var configuration =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(targetSchema)
            .defaultSchema(targetSchema)
            .locations("classpath:db/migration");
    if (target != null) {
      configuration.target(target);
    }
    return configuration.load();
  }

  private void insertUser(UUID userId, String zone, UUID organizationId) {
    jdbcTemplate.update(
        "INSERT INTO "
            + schema
            + ".users (id, email, normalized_email, full_name, status, actor_zone, organization_id) "
            + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
        userId,
        userId + "@example.test",
        userId + "@example.test",
        "Fixture " + zone,
        zone,
        organizationId);
  }
}
