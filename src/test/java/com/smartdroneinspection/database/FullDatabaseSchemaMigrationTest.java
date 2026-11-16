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
            "users",
            "auth_sessions",
            "prospective_customer_registrations",
            "drone_registration_applications",
            "drone_document_configs",
            "drone_feature_configs",
            "drones",
            "asset_categories",
            "checklist_templates",
            "inspection_items",
            "assets",
            "asset_documents",
            "inspections",
            "reinspection_defect_verifications",
            "field_sessions",
            "evidence",
            "evidence_upload_sessions",
            "findings",
            "inspection_reports",
            "audit_events",
            "notifications",
            "service_requests",
            "service_request_messages",
            "service_request_quotes",
            "service_request_occurrences",
            "service_invoices",
            "service_payments",
            "inspector_payouts",
            "service_refunds",
            "inspector_receivables",
            "inspector_receivable_recoveries",
            "inspection_finance_settings",
            "platform_ai_settings",
            "inspector_flight_logs",
            "inspector_profiles",
            "pilot_license_classes",
            "inspector_applications",
            "inspector_reliability_events",
            "otp_challenges",
            "event_publication",
            "flyway_schema_history");

    assertThat(expected).hasSize(41);
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(actual)
        .doesNotContain(
            "subscriptions",
            "payment_transactions",
            "platform_drones",
            "drone_leases",
            "recurring_inspection_schedules",
            "schedule_occurrences",
            "inspector_partner_applications",
            "login_otp_challenges",
            "registration_otp_challenges",
            "password_reset_otp_challenges",
            "maintenance_work_orders",
            "maintenance_tasks",
            "maintenance_team_members",
            "maintenance_estimate_versions",
            "maintenance_cost_lines",
            "maintenance_change_orders",
            "maintenance_work_logs",
            "maintenance_reports",
            "maintenance_acceptance_decisions",
            "repair_knowledge_chunks");
    assertThat(actual).contains("inspector_flight_logs", "reinspection_defect_verifications");
  }

  private void assertColumns(String table, String... expectedColumns) {
    List<String> columns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?",
            String.class,
            table);
    assertThat(columns).as("columns for %s", table).contains(expectedColumns);
  }

  private void assertNoColumn(String table, String column) {
    List<String> columns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?",
            String.class,
            table);
    assertThat(columns).as("columns for %s", table).doesNotContain(column);
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
            "uq_assets_owner_customer_asset_code",
            "ix_notifications_recipient_status",
            "ix_assets_owner_customer",
            "ix_drones_owner_customer");
    assertThat(constraints).contains("assets_owner_customer_id_fkey");
  }

  @Test
  void appliesConsolidatedV1BaselineAndMaintainsCleanTargetColumns() {
    List<String> versions =
        jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class);
    assertThat(versions).containsExactly("1");

    assertColumns(
        "inspections",
        "flight_permit_number",
        "flight_permit_authority",
        "flight_permit_valid_from",
        "flight_permit_valid_to",
        "flight_permit_drone_serial",
        "flight_permit_max_altitude_m",
        "flight_permit_file_sha256",
        "flight_permit_file_size_bytes",
        "flight_permit_content_type",
        "flight_permit_uploaded_at",
        "flight_permit_uploaded_by",
        "flight_permit_verification_status",
        "flight_permit_verified_by",
        "flight_permit_verified_at",
        "flight_permit_rejection_reason",
        "flight_permit_review_notes");

    assertColumns(
        "evidence",
        "original_size_bytes",
        "processing_status",
        "processing_error",
        "raw_object_key",
        "thumbnail_object_key",
        "thumbnail_size_bytes",
        "media_width",
        "media_height",
        "duration_seconds",
        "processed_at",
        "processing_attempts",
        "processing_locked_at");
    assertColumns(
        "evidence_upload_sessions",
        "id",
        "inspection_id",
        "field_session_id",
        "created_by_user_id",
        "file_name",
        "content_type",
        "declared_size_bytes",
        "chunk_size_bytes",
        "total_parts",
        "uploaded_parts_json",
        "status",
        "storage_upload_id",
        "raw_object_key",
        "evidence_id",
        "expires_at");

    assertColumns(
        "inspections",
        "source_inspection_id",
        "decline_reason",
        "declined_at",
        "declined_by_inspector_id",
        "cancellation_reason",
        "overdue_notified_at",
        "clarification_count",
        "assignment_status",
        "assignment_accepted_at");
    assertColumns("service_requests", "source_inspection_id");
    assertColumns(
        "reinspection_defect_verifications",
        "id",
        "reinspection_id",
        "source_finding_id",
        "new_evidence_id",
        "resolution_status",
        "inspector_notes",
        "created_at",
        "updated_at");
    assertNoColumn("inspections", "reassignment_required");
    assertNoColumn("inspection_reports", "clarification_requested");
    assertNoColumn("platform_ai_settings", "auto_confirmed_count");
    assertNoColumn("drones", "has_thermal_camera");
    assertNoColumn("drones", "has_rtk");
    assertNoColumn("drone_registration_applications", "ownership_file_key");
    assertNoColumn("drone_registration_applications", "inspection_file_key");
    assertNoColumn("drone_registration_applications", "has_thermal_camera");
    assertNoColumn("drone_registration_applications", "has_rtk");
    assertNoColumn("inspector_applications", "setup_email_sent_at");
    assertNoColumn("inspector_applications", "notification_attempts");
    assertNoColumn("inspector_applications", "last_notification_error");
    assertNoColumn("inspector_receivables", "created_at");
    assertNoColumn("audit_events", "reason");
    assertNoColumn("inspector_payouts", "refund_reconciliation_reference");
    assertNoColumn("inspector_payouts", "refund_reconciliation_note");
    assertNoColumn("inspector_payouts", "refund_reconciled_by_user_id");
    assertNoColumn("inspector_payouts", "refund_reconciled_at");
    assertNoColumn("service_refunds", "recorded_by_user_id");
    assertNoColumn("service_refunds", "recorded_at");
    assertColumns("field_sessions", "hung_notified_at", "aborted_by_user_id");
    assertColumns(
        "inspector_reliability_events",
        "id",
        "inspector_id",
        "inspection_id",
        "field_session_id",
        "event_type",
        "reason",
        "occurred_at");

    assertColumns("assets", "owner_customer_id");
    assertColumns("drones", "owner_customer_id");
    assertColumns("inspections", "owner_customer_id");
    assertColumns("service_requests", "owner_customer_id");
    assertNoColumn("assets", "organization_id");
    assertNoColumn("drones", "organization_id");
    assertNoColumn("inspections", "organization_id");
    assertNoColumn("service_requests", "organization_id");
    assertNoColumn("users", "organization_id");
    assertNoColumn("users", "actor_zone");

    // V43 dropped the organizations table outright; owner_customer_id is the sole tenant key.
    List<String> orgTables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'organizations'",
            String.class);
    assertThat(orgTables).as("organizations table must be dropped by V43").isEmpty();

    List<String> dimensionColumns =
        jdbcTemplate.queryForList(
            """
            SELECT table_name || '.' || column_name
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND column_name IN (
                'structure_height_meters', 'footprint_area_square_meters', 'max_altitude_meters')
              AND table_name IN ('assets', 'drones', 'drone_registration_applications', 'prospective_customer_registrations')
            """,
            String.class);
    assertThat(dimensionColumns)
        .containsExactlyInAnyOrder(
            "assets.structure_height_meters",
            "assets.footprint_area_square_meters",
            "drones.max_altitude_meters",
            "drone_registration_applications.max_altitude_meters",
            "prospective_customer_registrations.structure_height_meters",
            "prospective_customer_registrations.footprint_area_square_meters");

    String recurrenceConstraint =
        jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_service_requests_recurrence'",
            String.class);
    assertThat(recurrenceConstraint)
        .contains(
            "MONTH", "120", "DAY", "WEEK", "occurrence_count >= 2", "occurrence_count <= 100");

    List<String> sequences =
        jdbcTemplate.queryForList(
            """
            SELECT sequence_name
            FROM information_schema.sequences
            WHERE sequence_schema = current_schema()
            """,
            String.class);
    assertThat(sequences).doesNotContain("service_request_occurrences_payos_order_code_seq");

    List<String> occurrenceColumns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'service_request_occurrences'
            """,
            String.class);
    assertThat(occurrenceColumns)
        .contains("request_id", "inspector_id", "drone_id", "inspection_id")
        .doesNotContain(
            "total_price", "payment_status", "payos_order_code", "payment_method", "payout_status");

    assertColumns(
        "service_invoices", "occurrence_id", "total_price", "vat_rate", "report_accepted_at");
    assertColumns(
        "service_payments",
        "occurrence_id",
        "payment_status",
        "payos_order_code",
        "payment_method",
        "refunded_amount",
        "refund_status");
    assertColumns(
        "inspector_payouts",
        "occurrence_id",
        "inspector_id",
        "payout_status",
        "payout_reference",
        "payout_recorded_at",
        "payout_held_for_refund",
        "payout_recorded_by_user_id");
    assertColumns(
        "service_refunds",
        "id",
        "occurrence_id",
        "amount",
        "inspector_payout_reduction",
        "platform_commission_adjustment",
        "vat_adjustment",
        "tncn_adjustment",
        "platform_absorbed_amount",
        "refund_reference",
        "request_reference",
        "inspector_receivable_amount",
        "platform_absorbed_plan_amount",
        "refund_status",
        "initiated_by_user_id",
        "initiated_at",
        "completed_by_user_id",
        "completed_at",
        "cancelled_by_user_id",
        "cancelled_at",
        "cancellation_reason");
    assertColumns(
        "inspector_receivables",
        "id",
        "refund_id",
        "occurrence_id",
        "inspector_id",
        "assessed_amount",
        "source_reference",
        "reason",
        "recorded_by_user_id",
        "recorded_at",
        "row_version");
    assertColumns(
        "inspector_receivable_recoveries",
        "id",
        "receivable_id",
        "amount",
        "external_reference",
        "note",
        "recorded_by_user_id",
        "recorded_at");

    List<String> quoteColumns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'service_request_quotes'",
            String.class);
    assertThat(quoteColumns)
        .contains("request_id", "updated_at", "updated_by_user_id", "row_version")
        .doesNotContain("version_no");

    List<String> requestColumns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'service_requests'",
            String.class);
    assertThat(requestColumns)
        .contains(
            "accepted_quote_id", "accepted_at", "accepted_by_user_id", "accepted_terms_snapshot")
        .doesNotContain("accepted_quote_version");

    List<String> otpColumns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'otp_challenges'
            """,
            String.class);
    assertThat(otpColumns)
        .contains(
            "purpose",
            "challenge_hash",
            "user_id",
            "client_ip",
            "code_hash",
            "code_expires_at",
            "expires_at",
            "consumed_at",
            "failed_attempts",
            "send_count",
            "row_version");

    List<String> oldOtpTables =
        jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = current_schema()
              AND table_name IN (
                'login_otp_challenges',
                'registration_otp_challenges',
                'password_reset_otp_challenges')
            """,
            String.class);
    assertThat(oldOtpTables).isEmpty();

    List<String> profileColumns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'inspector_profiles'
            """,
            String.class);
    assertThat(profileColumns)
        .contains(
            "user_id",
            "pilot_license_number",
            "pilot_license_class",
            "pilot_license_expires_at",
            "pilot_license_file_key",
            "pilot_license_issued_date",
            "pilot_license_authority",
            "citizen_id",
            "date_of_birth",
            "address",
            "prior_flight_hours",
            "certified_drone_models",
            "specializations",
            "experience_summary",
            "notes",
            "updated_at");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'pilot_license_classes'",
                Integer.class))
        .isEqualTo(1);
  }
}
