package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PersistenceEntityOwnershipTest {

  @ParameterizedTest(name = "{0}.{1} is owned by {2}")
  @MethodSource("entityMappings")
  void mapsDatabaseTableToOwningFeature(String feature, String tableName, String entityClassName)
      throws ClassNotFoundException {
    Class<?> entityType = Class.forName(entityClassName);

    assertThat(entityType.getPackageName())
        .isEqualTo("com.smartdroneinspection." + feature + ".domain");
    assertThat(entityType.isAnnotationPresent(Entity.class)).isTrue();
    assertThat(entityType.getAnnotation(Table.class).name()).isEqualTo(tableName);
  }

  private static Stream<Arguments> entityMappings() {
    return Stream.of(
        Arguments.of(
            "users", "organizations", "com.smartdroneinspection.users.domain.Organization"),
        Arguments.of(
            "users",
            "security_audit_events",
            "com.smartdroneinspection.users.domain.SecurityAuditEvent"),
        Arguments.of(
            "inspections", "inspections", "com.smartdroneinspection.inspections.domain.Inspection"),
        Arguments.of(
            "inspections",
            "checklist_responses",
            "com.smartdroneinspection.inspections.domain.ChecklistResponse"),
        Arguments.of(
            "inspections", "evidence", "com.smartdroneinspection.inspections.domain.Evidence"),
        Arguments.of(
            "inspections",
            "ai_finding_candidates",
            "com.smartdroneinspection.inspections.domain.AiFindingCandidate"),
        Arguments.of(
            "inspections",
            "verified_findings",
            "com.smartdroneinspection.inspections.domain.VerifiedFinding"),
        Arguments.of(
            "inspections",
            "inspection_reports",
            "com.smartdroneinspection.inspections.domain.InspectionReport"),
        Arguments.of(
            "inspections",
            "report_versions",
            "com.smartdroneinspection.inspections.domain.ReportVersion"),
        Arguments.of(
            "inspections",
            "peer_reviews",
            "com.smartdroneinspection.inspections.domain.PeerReview"),
        Arguments.of(
            "maintenance",
            "maintenance_tickets",
            "com.smartdroneinspection.maintenance.domain.MaintenanceTicket"),
        Arguments.of(
            "maintenance",
            "maintenance_assignments",
            "com.smartdroneinspection.maintenance.domain.MaintenanceAssignment"),
        Arguments.of(
            "maintenance",
            "maintenance_assessments",
            "com.smartdroneinspection.maintenance.domain.MaintenanceAssessment"),
        Arguments.of(
            "maintenance",
            "maintenance_quotations",
            "com.smartdroneinspection.maintenance.domain.MaintenanceQuotation"),
        Arguments.of(
            "maintenance",
            "maintenance_orders",
            "com.smartdroneinspection.maintenance.domain.MaintenanceOrder"),
        Arguments.of(
            "maintenance",
            "maintenance_work_logs",
            "com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog"),
        Arguments.of(
            "maintenance",
            "maintenance_change_requests",
            "com.smartdroneinspection.maintenance.domain.MaintenanceChangeRequest"),
        Arguments.of(
            "maintenance",
            "maintenance_ticket_findings",
            "com.smartdroneinspection.maintenance.domain.MaintenanceTicketFinding"),
        Arguments.of(
            "maintenance", "invoices", "com.smartdroneinspection.maintenance.domain.Invoice"),
        Arguments.of(
            "notifications",
            "notifications",
            "com.smartdroneinspection.notifications.domain.Notification"));
  }
}
