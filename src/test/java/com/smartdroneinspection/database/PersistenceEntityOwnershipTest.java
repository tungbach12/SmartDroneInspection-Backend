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
            "inspection_flight_permits",
            "com.smartdroneinspection.inspections.domain.InspectionFlightPermit"),
        Arguments.of(
            "inspections",
            "field_sessions",
            "com.smartdroneinspection.inspections.domain.FieldSession"),
        Arguments.of(
            "inspections",
            "field_session_telemetry",
            "com.smartdroneinspection.inspections.domain.FieldSessionTelemetry"),
        Arguments.of(
            "inspections",
            "field_session_flights",
            "com.smartdroneinspection.inspections.domain.FieldSessionFlight"),
        Arguments.of(
            "inspections",
            "inspector_ratings",
            "com.smartdroneinspection.inspections.domain.InspectorRating"),
        Arguments.of(
            "inspections",
            "rating_criterion_configs",
            "com.smartdroneinspection.inspections.domain.RatingCriterionConfig"),
        Arguments.of(
            "inspections", "evidence", "com.smartdroneinspection.inspections.domain.Evidence"),
        Arguments.of(
            "inspections",
            "evidence_upload_sessions",
            "com.smartdroneinspection.inspections.domain.EvidenceUploadSession"),
        Arguments.of(
            "inspections", "findings", "com.smartdroneinspection.inspections.domain.Finding"),
        Arguments.of(
            "inspections",
            "inspection_reports",
            "com.smartdroneinspection.inspections.domain.InspectionReport"),
        Arguments.of(
            "contracts",
            "inspection_finance_settings",
            "com.smartdroneinspection.contracts.domain.InspectionFinanceSettings"),
        Arguments.of(
            "contracts",
            "gateway_inbound_transactions",
            "com.smartdroneinspection.contracts.domain.GatewayInboundTransaction"),
        Arguments.of(
            "contracts",
            "platform_operations_settings",
            "com.smartdroneinspection.contracts.domain.PlatformOperationsSettings"),
        Arguments.of(
            "inspections",
            "inspection_messages",
            "com.smartdroneinspection.inspections.domain.InspectionMessage"),
        Arguments.of(
            "inspections",
            "inspection_message_attachments",
            "com.smartdroneinspection.inspections.domain.InspectionMessageAttachment"),
        Arguments.of(
            "users",
            "inspector_flight_logs",
            "com.smartdroneinspection.users.domain.InspectorFlightLog"));
  }
}
