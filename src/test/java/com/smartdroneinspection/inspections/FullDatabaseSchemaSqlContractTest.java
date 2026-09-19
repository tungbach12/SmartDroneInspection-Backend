package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FullDatabaseSchemaSqlContractTest {

  @Test
  void includesAllTargetWorkflowTablesAndGuardRules() throws IOException {
    String wf3 = readMigration("V7__inspection_execution_and_reporting.sql");
    String wf4 = readMigration("V8__maintenance_and_billing.sql");
    String support = readMigration("V9__notifications.sql");

    assertThat(wf3)
        .contains("CREATE TABLE inspections")
        .contains("CREATE TABLE checklist_responses")
        .contains("CREATE TABLE evidence")
        .contains("CREATE TABLE ai_finding_candidates")
        .contains("CREATE TABLE verified_findings")
        .contains("CREATE TABLE inspection_reports")
        .contains("CREATE TABLE report_versions")
        .contains("CREATE TABLE peer_reviews")
        .contains("CONSTRAINT ck_evidence_parent CHECK")
        .contains("CREATE UNIQUE INDEX uq_evidence_inspection_checksum")
        .contains("CREATE UNIQUE INDEX uq_evidence_work_log_checksum")
        .contains("CONSTRAINT uq_report_versions_number UNIQUE")
        .contains("CONSTRAINT uq_peer_reviews_version UNIQUE");

    assertThat(wf4)
        .contains("CREATE TABLE maintenance_tickets")
        .contains("CREATE TABLE maintenance_ticket_findings")
        .contains("CREATE TABLE maintenance_assessments")
        .contains("CREATE TABLE maintenance_quotations")
        .contains("CREATE TABLE maintenance_orders")
        .contains("CREATE TABLE maintenance_assignments")
        .contains("CREATE TABLE maintenance_work_logs")
        .contains("CREATE TABLE maintenance_change_requests")
        .contains("CREATE TABLE invoices")
        .contains("CREATE UNIQUE INDEX uq_maintenance_assignments_active")
        .contains("ALTER TABLE evidence")
        .contains("ALTER TABLE maintenance_orders")
        .contains("ALTER TABLE maintenance_change_requests");

    assertThat(support)
        .contains("CREATE TABLE notifications")
        .contains("channel IN ('IN_APP', 'EMAIL')")
        .contains("status IN ('PENDING', 'SENT', 'FAILED', 'READ')")
        .contains("CREATE INDEX ix_notifications_recipient_status")
        .contains("CREATE INDEX ix_notifications_delivery");
  }

  @Test
  void definesAllThirtyFiveApplicationTablesAcrossMigrations() throws IOException {
    List<String> expectedTables =
        List.of(
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
            "notifications");

    String migrations =
        readMigration("V3__authentication.sql")
            + readMigration("V5__asset_catalog_and_planning.sql")
            + readMigration("V6__inspection_requests_and_assignments.sql")
            + readMigration("V7__inspection_execution_and_reporting.sql")
            + readMigration("V8__maintenance_and_billing.sql")
            + readMigration("V9__notifications.sql");
    String normalizedMigrations = migrations.replace("CREATE TABLE IF NOT EXISTS", "CREATE TABLE");

    assertThat(expectedTables)
        .allSatisfy(table -> assertThat(normalizedMigrations).contains("CREATE TABLE " + table));
  }

  private String readMigration(String filename) throws IOException {
    return Files.readString(Path.of("src/main/resources/db/migration", filename));
  }
}
