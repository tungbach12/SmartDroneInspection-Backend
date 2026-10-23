package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FullDatabaseSchemaMigrationTest {

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired DataSource dataSource;

  @Test
  void flywayCreatesExactlyTheTargetTablesAndFrameworkRegistry() {
    Set<String> actual =
        jdbcTemplate
            .queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class)
            .stream()
            .collect(Collectors.toSet());
    Set<String> expected =
        Set.of(
            "organizations",
            "subscriptions",
            "users",
            "user_roles",
            "auth_sessions",
            "refresh_tokens",
            "workforce_credentials",
            "drones",
            "drone_documents",
            "flight_permits",
            "asset_categories",
            "checklist_templates",
            "checklist_items",
            "assets",
            "asset_documents",
            "asset_pair_assignments",
            "inspection_schedules",
            "inspections",
            "inspection_preparations",
            "inspection_readiness_decisions",
            "field_sessions",
            "checklist_responses",
            "evidence",
            "findings",
            "inspection_reports",
            "inspection_report_versions",
            "report_version_evidence",
            "report_version_findings",
            "maintenance_work_orders",
            "maintenance_tasks",
            "maintenance_team_members",
            "maintenance_estimate_versions",
            "maintenance_cost_lines",
            "maintenance_change_orders",
            "maintenance_work_logs",
            "maintenance_report_versions",
            "maintenance_acceptance_decisions",
            "audit_events",
            "notifications",
            "payment_transactions",
            "repair_knowledge_chunks",
            "event_publication",
            "flyway_schema_history");

    assertThat(expected).hasSize(43);
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void baselineAndIncrementalMigrationPathsProduceEquivalentPostgresCatalogs() {
    String baselineSchema = "baseline_new_" + UUID.randomUUID().toString().replace("-", "");
    String incrementalSchema = "baseline_existing_" + UUID.randomUUID().toString().replace("-", "");
    try {
      jdbcTemplate.execute("CREATE SCHEMA " + baselineSchema);
      jdbcTemplate.execute("CREATE SCHEMA " + incrementalSchema);
      Flyway baselinePath = configureFlyway(baselineSchema, null, "public");
      baselinePath.migrate();

      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT version || ':' || type || ':' || description FROM "
                      + baselineSchema
                      + ".flyway_schema_history WHERE success",
                  String.class))
          .isEqualTo("43:SQL_BASELINE:complete schema");
      assertThat(tableNames(baselineSchema)).hasSize(42);
      assertThat(tableNames(baselineSchema))
          .contains(
              "event_publication",
              "findings",
              "audit_events",
              "payment_transactions",
              "repair_knowledge_chunks");

      Flyway throughV42 =
          configureFlyway(incrementalSchema, MigrationVersion.fromVersion("42"), "public");
      throughV42.migrate();
      Flyway incrementalPath = configureFlyway(incrementalSchema, null, "public");
      assertThat(incrementalPath.info().all())
          .filteredOn(migration -> "complete schema".equals(migration.getDescription()))
          .singleElement()
          .extracting(migration -> migration.getState().name())
          .isEqualTo("IGNORED");
      incrementalPath.migrate();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT version || ':' || type FROM "
                      + incrementalSchema
                      + ".flyway_schema_history WHERE success "
                      + "ORDER BY installed_rank DESC LIMIT 1",
                  String.class))
          .isEqualTo("43:SQL");

      assertThat(schemaCatalog(incrementalSchema))
          .containsExactlyElementsOf(schemaCatalog(baselineSchema));
    } finally {
      jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + baselineSchema + " CASCADE");
      jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + incrementalSchema + " CASCADE");
    }
  }

  private Set<String> tableNames(String schema) {
    return jdbcTemplate
        .queryForList(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_type = 'BASE TABLE' "
                + "AND table_name <> 'flyway_schema_history'",
            String.class,
            schema)
        .stream()
        .collect(Collectors.toSet());
  }

  private List<String> schemaCatalog(String schema) {
    List<String> catalog = new ArrayList<>();
    catalog.addAll(
        jdbcTemplate.query(
            "SELECT table_name, column_name, data_type, udt_name, is_nullable, "
                + "column_default, character_maximum_length, numeric_precision, numeric_scale, "
                + "datetime_precision FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name <> 'flyway_schema_history' "
                + "ORDER BY table_name, column_name",
            (result, rowNum) ->
                String.join(
                    "|",
                    "COLUMN",
                    Objects.toString(result.getString(1), "<null>"),
                    Objects.toString(result.getString(2), "<null>"),
                    Objects.toString(result.getString(3), "<null>"),
                    Objects.toString(result.getString(4), "<null>"),
                    Objects.toString(result.getString(5), "<null>"),
                    Objects.toString(result.getString(6), "<null>"),
                    Objects.toString(result.getString(7), "<null>"),
                    Objects.toString(result.getString(8), "<null>"),
                    Objects.toString(result.getString(9), "<null>"),
                    Objects.toString(result.getString(10), "<null>")),
            schema));
    catalog.addAll(
        jdbcTemplate.query(
            "SELECT relation.relname, constraint_row.conname, constraint_row.contype, "
                + "constraint_row.convalidated "
                + "FROM pg_constraint constraint_row "
                + "JOIN pg_class relation ON relation.oid = constraint_row.conrelid "
                + "JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace "
                + "WHERE namespace.nspname = ? AND relation.relname <> 'flyway_schema_history' "
                + "ORDER BY relation.relname, constraint_row.conname",
            (result, rowNum) ->
                String.join(
                    "|",
                    "CONSTRAINT",
                    result.getString(1),
                    result.getString(2),
                    result.getString(3),
                    result.getString(4)),
            schema));
    catalog.addAll(
        jdbcTemplate.query(
            "SELECT tablename, indexname, indexdef FROM pg_indexes "
                + "WHERE schemaname = ? AND tablename <> 'flyway_schema_history' "
                + "ORDER BY tablename, indexname",
            (result, rowNum) ->
                String.join(
                    "|",
                    "INDEX",
                    result.getString(1),
                    result.getString(2),
                    normalizeSchema(result.getString(3), schema)),
            schema));
    catalog.addAll(
        jdbcTemplate.query(
            "SELECT sequencename, data_type, start_value, min_value, max_value, increment, "
                + "cycle, cache_size FROM information_schema.sequences WHERE sequence_schema = ? "
                + "ORDER BY sequencename",
            (result, rowNum) ->
                String.join(
                    "|",
                    "SEQUENCE",
                    result.getString(1),
                    result.getString(2),
                    result.getString(3),
                    result.getString(4),
                    result.getString(5),
                    result.getString(6),
                    result.getString(7),
                    result.getString(8)),
            schema));
    catalog.addAll(
        jdbcTemplate.query(
            "SELECT trigger_rel.relname, trigger_row.tgname, pg_get_triggerdef(trigger_row.oid) "
                + "FROM pg_trigger trigger_row "
                + "JOIN pg_class trigger_rel ON trigger_rel.oid = trigger_row.tgrelid "
                + "JOIN pg_namespace namespace ON namespace.oid = trigger_rel.relnamespace "
                + "WHERE namespace.nspname = ? AND NOT trigger_row.tgisinternal "
                + "ORDER BY trigger_rel.relname, trigger_row.tgname",
            (result, rowNum) ->
                String.join(
                    "|",
                    "TRIGGER",
                    result.getString(1),
                    result.getString(2),
                    normalizeSchema(result.getString(3), schema)),
            schema));
    catalog.addAll(
        jdbcTemplate.query(
            "SELECT extname FROM pg_extension ORDER BY extname",
            (result, rowNum) -> "EXTENSION|" + result.getString(1)));
    return catalog.stream().sorted().toList();
  }

  private String normalizeSchema(String value, String schema) {
    if (value == null) return "<null>";
    return value.replace(quotedSchema(schema), "<schema>").replace(schema + ".", "<schema>.");
  }

  private String quotedSchema(String schema) {
    return "\"" + schema.replace("\"", "\"\"") + "\"";
  }

  private Flyway configureFlyway(String schema, MigrationVersion target) {
    return configureFlyway(schema, target, null);
  }

  private Flyway configureFlyway(
      String schema, MigrationVersion target, String baselineExtensionSchema) {
    var configuration =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration")
            .encoding("UTF-8");
    if (baselineExtensionSchema != null) {
      configuration.schemas(schema, baselineExtensionSchema);
    }
    if (target != null) {
      configuration.target(target);
      configuration
          .getPluginRegister()
          .getExact(BaselineMigrationConfigurationExtension.class)
          .setBaselineMigrationPrefix("DISABLED_B");
    }
    return configuration.load();
  }

  @Test
  void createsTargetAndSharedRuntimeGuardIndexes() {
    List<String> indexes =
        jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = current_schema()
            """,
            String.class);
    List<String> constraints =
        jdbcTemplate.queryForList(
            """
            SELECT conname
            FROM pg_constraint
            WHERE connamespace = current_schema()::regnamespace
            """,
            String.class);

    assertThat(indexes)
        .contains(
            "uq_assets_organization_asset_code",
            "uq_asset_pair_assignments_active_asset",
            "uq_inspections_schedule_due_cycle",
            "uq_evidence_inspection_checksum",
            "ix_notifications_recipient_status",
            "ix_notifications_delivery",
            "uq_users_id_organization",
            "uq_assets_id_organization",
            "uq_asset_pair_assignments_id_scope");
    assertThat(constraints)
        .contains(
            "fk_asset_pair_assignments_asset_tenant",
            "fk_asset_pair_assignments_inspector_tenant",
            "fk_asset_pair_assignments_drone_tenant",
            "fk_inspections_asset_tenant",
            "fk_inspections_pair_tenant",
            "fk_maintenance_work_orders_asset_tenant");
  }
}
