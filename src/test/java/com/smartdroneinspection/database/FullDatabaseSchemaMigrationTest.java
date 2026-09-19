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
  void flywayCreatesAllApplicationTablesAndFrameworkRegistry() {
    List<String> names =
        jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
            """,
            String.class);

    Set<String> expected =
        Set.of(
            "organizations",
            "users",
            "user_roles",
            "auth_sessions",
            "refresh_tokens",
            "security_audit_events",
            "asset_categories",
            "checklist_templates",
            "checklist_items",
            "assets",
            "asset_documents",
            "inspection_schedules",
            "inspection_requests",
            "inspection_request_attachments",
            "inspection_quotations",
            "inspection_service_orders",
            "inspection_assignments",
            "inspections",
            "checklist_responses",
            "evidence",
            "ai_finding_candidates",
            "verified_findings",
            "inspection_reports",
            "report_versions",
            "peer_reviews",
            "maintenance_tickets",
            "maintenance_ticket_findings",
            "maintenance_assessments",
            "maintenance_quotations",
            "maintenance_orders",
            "maintenance_assignments",
            "maintenance_work_logs",
            "maintenance_change_requests",
            "invoices",
            "notifications",
            "event_publication");

    assertThat(names.stream().collect(Collectors.toSet())).containsAll(expected);
  }

  @Test
  void createsWorkflowGuardIndexes() {
    List<String> names =
        jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'public'
            """,
            String.class);

    assertThat(names)
        .contains(
            "uq_inspection_requests_periodic_due_cycle",
            "uq_inspection_assignments_active_order",
            "uq_evidence_inspection_checksum",
            "uq_evidence_work_log_checksum",
            "uq_maintenance_assignments_active",
            "ix_notifications_recipient_status",
            "ix_notifications_delivery");
  }
}
