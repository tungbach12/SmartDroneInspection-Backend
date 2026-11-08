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
        Arguments.of("assets", "assets", "com.smartdroneinspection.assets.domain.Asset"),
        Arguments.of("assets", "drones", "com.smartdroneinspection.assets.domain.Drone"),
        Arguments.of(
            "assets", "asset_categories", "com.smartdroneinspection.assets.domain.AssetCategory"),
        Arguments.of(
            "assets",
            "checklist_templates",
            "com.smartdroneinspection.assets.domain.ChecklistTemplate"),
        Arguments.of(
            "assets", "inspection_items", "com.smartdroneinspection.assets.domain.InspectionItem"),
        Arguments.of(
            "assets", "asset_documents", "com.smartdroneinspection.assets.domain.AssetDocument"),
        Arguments.of(
            "notifications",
            "notifications",
            "com.smartdroneinspection.notifications.domain.Notification"),
        Arguments.of("users", "auth_sessions", "com.smartdroneinspection.users.domain.AuthSession"),
        Arguments.of(
            "users", "organizations", "com.smartdroneinspection.users.domain.Organization"),
        Arguments.of("users", "users", "com.smartdroneinspection.users.domain.User"),
        Arguments.of(
            "users",
            "inspector_profiles",
            "com.smartdroneinspection.users.domain.InspectorProfile"),
        Arguments.of(
            "users", "otp_challenges", "com.smartdroneinspection.users.domain.OtpChallenge"),
        Arguments.of(
            "inspections", "inspections", "com.smartdroneinspection.inspections.domain.Inspection"),
        Arguments.of(
            "inspections",
            "field_sessions",
            "com.smartdroneinspection.inspections.domain.FieldSession"),
        Arguments.of(
            "inspections", "evidence", "com.smartdroneinspection.inspections.domain.Evidence"),
        Arguments.of(
            "inspections", "findings", "com.smartdroneinspection.inspections.domain.Finding"),
        Arguments.of(
            "inspections",
            "inspection_reports",
            "com.smartdroneinspection.inspections.domain.InspectionReport"),
        Arguments.of(
            "maintenance",
            "maintenance_work_orders",
            "com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder"),
        Arguments.of(
            "maintenance",
            "maintenance_tasks",
            "com.smartdroneinspection.maintenance.domain.MaintenanceTask"),
        Arguments.of(
            "maintenance",
            "maintenance_team_members",
            "com.smartdroneinspection.maintenance.domain.MaintenanceTeamMember"),
        Arguments.of(
            "maintenance",
            "maintenance_estimate_versions",
            "com.smartdroneinspection.maintenance.domain.MaintenanceEstimateVersion"),
        Arguments.of(
            "maintenance",
            "maintenance_cost_lines",
            "com.smartdroneinspection.maintenance.domain.MaintenanceCostLine"),
        Arguments.of(
            "maintenance",
            "maintenance_change_orders",
            "com.smartdroneinspection.maintenance.domain.MaintenanceChangeOrder"),
        Arguments.of(
            "maintenance",
            "maintenance_work_logs",
            "com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog"),
        Arguments.of(
            "maintenance",
            "maintenance_reports",
            "com.smartdroneinspection.maintenance.domain.MaintenanceReportVersion"),
        Arguments.of(
            "maintenance",
            "maintenance_acceptance_decisions",
            "com.smartdroneinspection.maintenance.domain.MaintenanceAcceptanceDecision"),
        Arguments.of(
            "contracts",
            "inspection_finance_settings",
            "com.smartdroneinspection.contracts.domain.InspectionFinanceSettings"),
        Arguments.of(
            "maintenance",
            "repair_knowledge_chunks",
            "com.smartdroneinspection.maintenance.domain.RepairKnowledgeChunk"),
        Arguments.of(
            "users",
            "inspector_flight_logs",
            "com.smartdroneinspection.users.domain.InspectorFlightLog"));
  }
}
