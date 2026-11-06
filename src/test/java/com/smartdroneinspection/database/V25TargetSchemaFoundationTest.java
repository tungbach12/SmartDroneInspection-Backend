package com.smartdroneinspection.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class V25TargetSchemaFoundationTest {

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void createsTheTargetColumnContractAcrossAllTargetTables() {
    Map<String, Set<String>> targetColumns =
        Map.ofEntries(
            Map.entry("organizations", Set.of("display_name", "registration_code", "status")),
            Map.entry(
                "users",
                Set.of(
                    "organization_id",
                    "normalized_email",
                    "full_name",
                    "auth_version",
                    "roles",
                    "actor_zone")),
            Map.entry(
                "inspector_profiles",
                Set.of(
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
                    "updated_at")),
            Map.entry(
                "otp_challenges",
                Set.of(
                    "id",
                    "purpose",
                    "challenge_hash",
                    "user_id",
                    "destination",
                    "channel",
                    "client_ip",
                    "code_hash",
                    "code_expires_at",
                    "expires_at",
                    "consumed_at",
                    "failed_attempts",
                    "send_count",
                    "last_sent_at",
                    "created_at",
                    "row_version")),
            Map.entry(
                "auth_sessions",
                Set.of("user_id", "client_type", "token_hash", "expires_at", "revoked_at")),
            Map.entry(
                "drones",
                Set.of(
                    "organization_id",
                    "serial_number",
                    "model",
                    "serviceability",
                    "registration_number",
                    "registration_file_key",
                    "registration_expires_at")),
            Map.entry("asset_categories", Set.of("code", "name", "active")),
            Map.entry(
                "checklist_templates",
                Set.of("template_type", "category_id", "label", "sort_order", "active")),
            Map.entry(
                "assets",
                Set.of(
                    "organization_id",
                    "asset_code",
                    "category_id",
                    "location",
                    "inspection_items",
                    "status")),
            Map.entry(
                "asset_documents",
                Set.of("asset_id", "document_type", "object_key", "checksum_sha256")),
            Map.entry(
                "inspections",
                Set.of(
                    "organization_id",
                    "asset_id",
                    "planned_start_at",
                    "planned_end_at",
                    "inspector_id",
                    "drone_id",
                    "flight_permit_file_key",
                    "flight_permit_file_name",
                    "status")),
            Map.entry(
                "field_sessions",
                Set.of(
                    "inspection_id",
                    "status",
                    "started_at",
                    "ended_at",
                    "pre_flight_checklist_note",
                    "outcome_notes")),
            Map.entry(
                "evidence",
                Set.of(
                    "inspection_id",
                    "field_session_id",
                    "file_name",
                    "content_type",
                    "size_bytes",
                    "object_key",
                    "capture_time",
                    "latitude",
                    "longitude",
                    "location_description",
                    "is_deleted",
                    "created_at")),
            Map.entry(
                "findings",
                Set.of(
                    "inspection_id",
                    "evidence_id",
                    "finding_code",
                    "defect_label",
                    "severity",
                    "bounding_box",
                    "technical_notes",
                    "recommended_action",
                    "status",
                    "model_name",
                    "confidence",
                    "forwarded_to_maintenance_at",
                    "is_deleted",
                    "created_at")),
            Map.entry(
                "inspection_reports",
                Set.of(
                    "inspection_id",
                    "version_no",
                    "status",
                    "submitted_at",
                    "reviewer_user_id",
                    "reviewed_at",
                    "return_reason",
                    "published_at",
                    "created_at")),
            Map.entry(
                "maintenance_work_orders",
                Set.of(
                    "organization_id",
                    "asset_id",
                    "source_report_version_id",
                    "source_finding_id",
                    "status",
                    "priority",
                    "due_at",
                    "corrective_scope",
                    "acceptance_criteria",
                    "budget_approver_user_id",
                    "team_lead_user_id",
                    "report_author_user_id",
                    "accepting_reviewer_user_id",
                    "created_at",
                    "updated_at",
                    "row_version")),
            Map.entry(
                "maintenance_tasks",
                Set.of(
                    "work_order_id",
                    "task_number",
                    "name",
                    "method",
                    "assigned_engineer_user_id",
                    "status",
                    "created_at",
                    "row_version")),
            Map.entry(
                "maintenance_team_members",
                Set.of(
                    "work_order_id",
                    "engineer_user_id",
                    "member_role",
                    "effective_from",
                    "effective_until",
                    "assigned_by_user_id",
                    "reason",
                    "active",
                    "created_at")),
            Map.entry(
                "maintenance_estimate_versions",
                Set.of(
                    "work_order_id",
                    "version_no",
                    "status",
                    "prepared_by_user_id",
                    "approved_by_user_id",
                    "approved_at",
                    "currency",
                    "baseline_total",
                    "assumptions",
                    "created_at")),
            Map.entry(
                "maintenance_cost_lines",
                Set.of(
                    "work_order_id",
                    "estimate_version_id",
                    "task_id",
                    "line_kind",
                    "state",
                    "description",
                    "quantity",
                    "unit",
                    "unit_rate",
                    "amount",
                    "currency",
                    "entered_by_user_id",
                    "created_at")),
            Map.entry(
                "maintenance_change_orders",
                Set.of(
                    "work_order_id",
                    "change_number",
                    "reason",
                    "proposed_delta",
                    "status",
                    "requested_by_user_id",
                    "decided_by_user_id",
                    "decided_at",
                    "decision_reason",
                    "created_at",
                    "updated_at",
                    "row_version")),
            Map.entry(
                "maintenance_work_logs",
                Set.of(
                    "work_order_id",
                    "task_id",
                    "engineer_user_id",
                    "started_at",
                    "ended_at",
                    "hours",
                    "as_left_condition",
                    "test_readings",
                    "status",
                    "created_at",
                    "row_version")),
            Map.entry(
                "maintenance_reports",
                Set.of(
                    "work_order_id",
                    "version_no",
                    "status",
                    "author_verified_at",
                    "content_snapshot",
                    "created_at")),
            Map.entry(
                "maintenance_acceptance_decisions",
                Set.of(
                    "work_order_id",
                    "report_version_id",
                    "reviewer_user_id",
                    "decision",
                    "technical_comments",
                    "decided_at")),
            Map.entry(
                "audit_events",
                Set.of(
                    "organization_id",
                    "actor_user_id",
                    "action",
                    "aggregate_type",
                    "aggregate_id",
                    "created_at")),
            Map.entry(
                "notifications",
                Set.of(
                    "recipient_user_id", "organization_id", "event_type", "channel", "created_at")),
            Map.entry(
                "service_request_occurrences",
                Set.of("request_id", "inspection_id", "inspector_id", "drone_id", "status")),
            Map.entry(
                "service_invoices",
                Set.of("occurrence_id", "total_price", "vat_rate", "report_accepted_at")),
            Map.entry(
                "service_payments",
                Set.of("occurrence_id", "payment_status", "payos_order_code", "payment_method")),
            Map.entry(
                "inspector_payouts",
                Set.of(
                    "occurrence_id",
                    "inspector_id",
                    "payout_status",
                    "payout_reference",
                    "payout_recorded_at")));

    assertThat(targetColumns).hasSize(30);
    for (Map.Entry<String, Set<String>> contract : targetColumns.entrySet()) {
      Set<String> actualColumns =
          jdbcTemplate
              .queryForList(
                  """
                  SELECT column_name
                  FROM information_schema.columns
                  WHERE table_schema = 'public' AND table_name = ?
                  """,
                  String.class,
                  contract.getKey())
              .stream()
              .collect(Collectors.toSet());
      assertThat(actualColumns)
          .as("target columns for table %s", contract.getKey())
          .containsAll(contract.getValue());
    }
  }

  @Test
  void removesSupersededAndLegacyOtpTables() {
    Set<String> actualTables =
        jdbcTemplate
            .queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class)
            .stream()
            .collect(Collectors.toSet());

    assertThat(actualTables)
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
            "password_reset_otp_challenges");
  }

  @Test
  void addsAnExplicitWorkOrderStatusCheck() {
    String statusCheck =
        jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_maintenance_work_orders_status'",
            String.class);

    assertThat(statusCheck)
        .contains("DRAFT")
        .contains("AWAITING_APPROVAL")
        .contains("APPROVED")
        .contains("IN_PROGRESS")
        .contains("CLOSED");
  }

  @Test
  void enforcesTargetEvidenceInspectionForeignKey() {
    String inspectionForeignKey =
        jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'fk_evidence_inspection'",
            String.class);

    assertThat(inspectionForeignKey)
        .contains("FOREIGN KEY (inspection_id)")
        .contains("inspections")
        .contains("ON DELETE RESTRICT");
  }

  @Test
  void rejectsEvidenceWithMissingOrOrphanedInspectionParent() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO evidence
                      (id, file_name, content_type, size_bytes,
                       object_key)
                    VALUES (?, 'file', 'text/plain', 1, ?)
                    """,
                    UUID.randomUUID(),
                    "no-parent-" + UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO evidence
                      (id, inspection_id, file_name, content_type, size_bytes,
                       object_key)
                    VALUES (?, ?, 'file', 'text/plain', 1, ?)
                    """,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "orphan-inspection-" + UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void limitsAssetStatusToTheTargetCatalogValues() {
    String statusCheck =
        jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_assets_status'",
            String.class);

    assertThat(statusCheck)
        .contains("ACTIVE")
        .contains("INACTIVE")
        .contains("RETIRED")
        .doesNotContain("PENDING_REVIEW")
        .doesNotContain("REJECTED");
  }

  @Test
  void keepsV24RoleConstraintAndProviderColumnsRetired() {
    String roleCheck =
        jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_users_roles'",
            String.class);
    Set<String> userColumns =
        jdbcTemplate
            .queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'users'",
                String.class)
            .stream()
            .collect(Collectors.toSet());

    assertThat(roleCheck)
        .contains("ADMIN")
        .contains("MANAGER")
        .contains("CUSTOMER")
        .contains("INSPECTOR")
        .contains("MAINTENANCE_ENGINEER");
    assertThat(userColumns)
        .doesNotContain("provider_id", "activation_token_hash", "activation_expires_at");
  }
}
