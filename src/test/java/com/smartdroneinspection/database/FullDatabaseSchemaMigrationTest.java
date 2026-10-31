package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FullDatabaseSchemaMigrationTest {

  @Autowired JdbcTemplate jdbcTemplate;

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
            "auth_sessions",
            "platform_drones",
            "drone_leases",
            "prospective_customer_registrations",
            "inspector_partner_applications",
            "drone_registration_applications",
            "drones",
            "asset_categories",
            "assets",
            "asset_documents",
            "inspections",
            "recurring_inspection_schedules",
            "schedule_occurrences",
            "field_sessions",
            "evidence",
            "findings",
            "inspection_reports",
            "maintenance_work_orders",
            "maintenance_tasks",
            "maintenance_team_members",
            "maintenance_estimate_versions",
            "maintenance_cost_lines",
            "maintenance_change_orders",
            "maintenance_work_logs",
            "maintenance_reports",
            "maintenance_acceptance_decisions",
            "audit_events",
            "notifications",
            "reinspection_defect_verifications",
            "payment_transactions",
            "repair_knowledge_chunks",
            "contract_partner_inspectors",
            "contract_partner_drones",
            "service_requests",
            "service_request_messages",
            "service_request_quotes",
            "service_request_occurrences",
            "inspection_finance_settings",
            "event_publication",
            "flyway_schema_history");

    assertThat(expected).hasSize(43);
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
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
            "ix_notifications_recipient_status",
            "ix_notifications_delivery",
            "uq_users_id_organization",
            "uq_assets_id_organization");
    assertThat(constraints)
        .contains("fk_inspections_asset_tenant", "fk_maintenance_work_orders_asset_tenant");
  }

  @Test
  void appliesAllMigrationsUpToV16AndCreatesPayOSSupport() {
    List<String> versions =
        jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class);
    assertThat(versions).hasSize(16);
    assertThat(versions.getLast()).isEqualTo("16");

    List<String> sequences =
        jdbcTemplate.queryForList(
            """
            SELECT sequence_name
            FROM information_schema.sequences
            WHERE sequence_schema = current_schema()
            """,
            String.class);
    assertThat(sequences).contains("service_request_occurrences_payos_order_code_seq");

    List<String> columns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'service_request_occurrences'
            """,
            String.class);
    assertThat(columns).contains("payos_order_code", "payos_payment_link_id", "payment_method");
  }
}
