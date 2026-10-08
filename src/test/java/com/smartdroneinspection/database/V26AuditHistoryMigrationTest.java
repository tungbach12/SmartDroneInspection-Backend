package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V26 drops the legacy {@code security_audit_events} table. Historical security audit rows must be
 * carried into the target {@code audit_events} table first, because SRS 3.1.4 requires
 * consequential transitions to stay attributable.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class V26AuditHistoryMigrationTest {

  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void carriesLegacySecurityAuditHistoryIntoAuditEventsBeforeDroppingTheTable() {
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("migration_v26_audit")
        .defaultSchema("migration_v26_audit")
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("25"))
        .load()
        .migrate();

    UUID actor = UUID.randomUUID();
    UUID subject = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO migration_v26_audit.users"
            + " (id, email, normalized_email, full_name, status, actor_zone)"
            + " VALUES (?, ?, ?, 'Fixture', 'ACTIVE', 'PLATFORM'),"
            + " (?, ?, ?, 'Fixture', 'ACTIVE', 'PLATFORM')",
        actor,
        actor + "@example.test",
        actor + "@example.test",
        subject,
        subject + "@example.test",
        subject + "@example.test");

    jdbcTemplate.update(
        "INSERT INTO migration_v26_audit.security_audit_events"
            + " (actor_user_id, subject_user_id, event_type, outcome, ip_address, user_agent,"
            + "  correlation_id)"
            + " VALUES (?, ?, 'AUTH_LOGIN', 'FAILURE', '203.0.113.9', 'agent', 'corr-1')",
        actor,
        subject);

    Flyway.configure()
        .dataSource(dataSource)
        .schemas("migration_v26_audit")
        .defaultSchema("migration_v26_audit")
        .locations("classpath:db/migration")
        .load()
        .migrate();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM migration_v26_audit.audit_events"
                    + " WHERE action = 'AUTH_LOGIN' AND after_status = 'FAILURE'",
                Integer.class))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM migration_v26_audit.audit_events"
                    + " WHERE action = 'AUTH_LOGIN'"
                    + " AND safe_metadata->>'ip_address' = '203.0.113.9'"
                    + " AND safe_metadata->>'user_agent' = 'agent'",
                Integer.class))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables"
                    + " WHERE table_schema = 'migration_v26_audit'"
                    + " AND table_name = 'security_audit_events'",
                Integer.class))
        .isZero();
  }
}
