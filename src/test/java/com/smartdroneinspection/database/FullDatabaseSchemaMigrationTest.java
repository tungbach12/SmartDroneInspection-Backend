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
  void flywayCreatesExactlyTheFortyOneTargetTablesAndFrameworkRegistry() {
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
            "evidence_quality_decisions",
            "ai_finding_candidates",
            "verified_findings",
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
