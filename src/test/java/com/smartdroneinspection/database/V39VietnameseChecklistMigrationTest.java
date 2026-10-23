package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class V39VietnameseChecklistMigrationTest {

  @Test
  void preservesExistingChecklistRowsAndAddsNullableVietnameseColumns() {
    try (var postgres = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17"))) {
      postgres.start();
      Flyway beforeV39 = configure(postgres, MigrationVersion.fromVersion("38"));
      beforeV39.migrate();

      UUID creatorId = UUID.randomUUID();
      UUID templateId = UUID.randomUUID();
      UUID itemId = UUID.randomUUID();
      try (var connection = postgres.createConnection("");
          var statement = connection.createStatement()) {
        statement.executeUpdate(
            "INSERT INTO users (id, email, normalized_email, full_name, status, actor_zone, "
                + "auth_version, failed_login_count, must_change_password) VALUES ('"
                + creatorId
                + "', 'migration@example.test', 'migration@example.test', 'Migration Tester', "
                + "'ACTIVE', 'PLATFORM', 0, 0, FALSE)");
        statement.executeUpdate(
            "INSERT INTO checklist_templates (id, template_key, version_number, name, description, "
                + "status, created_by_user_id) VALUES ('"
                + templateId
                + "', 'BRIDGE-BASIC', 1, 'Bridge Basic', 'Inspect the bridge', 'DRAFT', '"
                + creatorId
                + "')");
        statement.executeUpdate(
            "INSERT INTO checklist_items (id, template_id, item_code, section_name, prompt, "
                + "response_type, required, display_order, guidance) VALUES ('"
                + itemId
                + "', '"
                + templateId
                + "', 'SURFACE-CRACK', 'Structure', 'Check visible surface cracks', "
                + "'PASS_FAIL', TRUE, 0, 'Look for cracks')");
      }

      Flyway afterV39 = configure(postgres, null);
      afterV39.migrate();

      try (var connection = postgres.createConnection("");
          var statement = connection.createStatement();
          var result =
              statement.executeQuery(
                  "SELECT template.name, template.description, template.name_vi, "
                      + "template.description_vi, item.section_name, item.section_name_vi, "
                      + "item.prompt, item.prompt_vi, item.guidance, item.guidance_vi "
                      + "FROM checklist_templates template "
                      + "JOIN checklist_items item ON item.template_id = template.id "
                      + "WHERE template.id = '"
                      + templateId
                      + "' AND item.id = '"
                      + itemId
                      + "'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("name")).isEqualTo("Bridge Basic");
        assertThat(result.getString("description")).isEqualTo("Inspect the bridge");
        assertThat(result.getString("name_vi")).isNull();
        assertThat(result.getString("description_vi")).isNull();
        assertThat(result.getString("section_name")).isEqualTo("Structure");
        assertThat(result.getString("section_name_vi")).isNull();
        assertThat(result.getString("prompt")).isEqualTo("Check visible surface cracks");
        assertThat(result.getString("prompt_vi")).isNull();
        assertThat(result.getString("guidance")).isEqualTo("Look for cracks");
        assertThat(result.getString("guidance_vi")).isNull();
      }
      assertThat(afterV39.info().applied())
          .anySatisfy(migration -> assertThat(migration.getVersion().getVersion()).isEqualTo("39"));
    } catch (Exception exception) {
      throw new AssertionError(
          "Could not verify V39 against populated pre-migration rows", exception);
    }
  }

  private Flyway configure(PostgreSQLContainer postgres, MigrationVersion target) {
    var configuration =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration");
    if (target != null) {
      configuration.target(target);
      configuration
          .getPluginRegister()
          .getExact(BaselineMigrationConfigurationExtension.class)
          .setBaselineMigrationPrefix("DISABLED_B");
    }
    return configuration.load();
  }
}
