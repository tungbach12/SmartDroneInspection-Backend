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
            Map.entry(
                "organizations",
                Set.of(
                    "legal_name",
                    "display_name",
                    "registration_code",
                    "timezone",
                    "status",
                    "created_by_user_id",
                    "row_version")),
            Map.entry(
                "subscriptions",
                Set.of(
                    "organization_id",
                    "plan_code",
                    "billing_period_months",
                    "status",
                    "starts_at",
                    "ends_at",
                    "terms_snapshot")),
            Map.entry(
                "users",
                Set.of(
                    "organization_id", "normalized_email", "full_name", "auth_version", "roles")),
            Map.entry(
                "auth_sessions",
                Set.of(
                    "user_id",
                    "client_type",
                    "token_hash",
                    "issued_at",
                    "expires_at",
                    "revoked_at")),
            Map.entry(
                "workforce_credentials",
                Set.of("organization_id", "user_id", "credential_type", "status", "expires_at")),
            Map.entry(
                "drones", Set.of("organization_id", "serial_number", "model", "serviceability")),
            Map.entry(
                "drone_documents",
                Set.of("drone_id", "document_type", "object_key", "checksum_sha256", "status")),
            Map.entry(
                "flight_permits",
                Set.of("organization_id", "permit_type", "valid_from", "valid_until", "status")),
            Map.entry(
                "asset_categories",
                Set.of(
                    "code",
                    "name",
                    "description",
                    "active",
                    "created_at",
                    "updated_at",
                    "row_version")),
            Map.entry(
                "checklist_templates",
                Set.of(
                    "template_key",
                    "version_number",
                    "asset_category_id",
                    "name",
                    "description",
                    "name_vi",
                    "description_vi",
                    "status",
                    "created_by_user_id",
                    "published_at",
                    "created_at",
                    "updated_at",
                    "row_version")),
            Map.entry(
                "checklist_items",
                Set.of(
                    "template_id",
                    "item_code",
                    "section_name",
                    "prompt",
                    "response_type",
                    "required",
                    "display_order",
                    "guidance",
                    "section_name_vi",
                    "prompt_vi",
                    "guidance_vi",
                    "validation_config",
                    "created_at")),
            Map.entry(
                "assets",
                Set.of(
                    "organization_id",
                    "asset_code",
                    "category_id",
                    "name",
                    "description",
                    "location",
                    "latitude",
                    "longitude",
                    "technical_profile",
                    "status",
                    "created_at",
                    "updated_at",
                    "row_version")),
            Map.entry(
                "asset_documents",
                Set.of(
                    "asset_id",
                    "document_type",
                    "file_name",
                    "content_type",
                    "size_bytes",
                    "object_key",
                    "checksum_sha256",
                    "document_date",
                    "uploaded_by_user_id",
                    "created_at")),
            Map.entry(
                "asset_pair_assignments",
                Set.of(
                    "organization_id",
                    "asset_id",
                    "inspector_user_id",
                    "drone_id",
                    "valid_from",
                    "status")),
            Map.entry(
                "inspections",
                Set.of(
                    "organization_id",
                    "asset_id",
                    "asset_pair_assignment_id",
                    "objective",
                    "scope",
                    "component_scope",
                    "acceptance_criteria",
                    "planned_start_at",
                    "planned_end_at",
                    "inspector_id",
                    "drone_id",
                    "status",
                    "findings_confirmed_at",
                    "findings_snapshot_hash",
                    "created_at",
                    "updated_at",
                    "row_version")),
            Map.entry(
                "inspection_preparations",
                Set.of(
                    "inspection_id",
                    "inspector_user_id",
                    "preparation_version",
                    "shot_list",
                    "status")),
            Map.entry(
                "inspection_readiness_decisions",
                Set.of(
                    "inspection_id",
                    "decision",
                    "reviewed_by_user_id",
                    "decided_at",
                    "preparation_version",
                    "source_hash")),
            Map.entry(
                "field_sessions",
                Set.of(
                    "inspection_id",
                    "organization_id",
                    "inspector_user_id",
                    "status",
                    "started_at",
                    "ended_at")),
            Map.entry(
                "checklist_responses",
                Set.of(
                    "inspection_id",
                    "context",
                    "template_version",
                    "responder_user_id",
                    "answer",
                    "created_at")),
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
                    "content_snapshot",
                    "submitted_at",
                    "reviewer_user_id",
                    "reviewed_at",
                    "return_reason",
                    "rendered_object_key",
                    "rendered_checksum_sha256",
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
                    "owner_user_id",
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
                    "planned_start_at",
                    "planned_end_at",
                    "acceptance_criteria",
                    "status",
                    "created_at",
                    "updated_at",
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
                    "updated_at",
                    "row_version")),
            Map.entry(
                "maintenance_reports",
                Set.of(
                    "work_order_id",
                    "version_no",
                    "status",
                    "author_verified_at",
                    "content_snapshot",
                    "rendered_object_key",
                    "rendered_checksum_sha256",
                    "created_at")),
            Map.entry(
                "maintenance_acceptance_decisions",
                Set.of(
                    "work_order_id",
                    "report_version_id",
                    "reviewer_user_id",
                    "decision",
                    "technical_comments",
                    "acceptance_checklist",
                    "test_result",
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
                    "recipient_user_id",
                    "organization_id",
                    "event_type",
                    "aggregate_type",
                    "aggregate_id",
                    "created_at")));

    assertThat(targetColumns).hasSize(33);
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
  void rejectsAnAssetPairWhoseInspectorBelongsToAnotherOrganization() {
    UUID primaryOrganization = UUID.randomUUID();
    UUID otherOrganization = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations
          (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE'),
               (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        primaryOrganization,
        "Primary " + primaryOrganization,
        "Primary " + primaryOrganization,
        "ORG-" + primaryOrganization,
        otherOrganization,
        "Other " + otherOrganization,
        "Other " + otherOrganization,
        "ORG-" + otherOrganization);
    UUID primaryAdmin = insertOrgAdmin(primaryOrganization, "primary-admin");
    UUID foreignInspector = insertOrgAdmin(otherOrganization, "foreign-inspector");
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO asset_categories (id, code, name) VALUES (?, ?, ?)",
        categoryId,
        "CAT-" + categoryId.toString().toUpperCase(),
        "Test category");
    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO assets
          (id, organization_id, category_id, asset_code, name, location, status)
        VALUES (?, ?, ?, ?, 'Test asset', '{"label":"Test location"}'::jsonb, 'ACTIVE')
        """,
        assetId,
        primaryOrganization,
        categoryId,
        "ASSET-" + assetId.toString().toUpperCase());
    UUID droneId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO drones (id, organization_id, serial_number, serviceability) VALUES (?, ?, ?, 'ACTIVE')",
        droneId,
        primaryOrganization,
        "DRONE-" + droneId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO asset_pair_assignments
                      (id, organization_id, asset_id, inspector_user_id, drone_id, valid_from,
                       status, assigned_by_user_id)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE', ?)
                    """,
                    UUID.randomUUID(),
                    primaryOrganization,
                    assetId,
                    foreignInspector,
                    droneId,
                    primaryAdmin))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private UUID insertOrgAdmin(UUID organizationId, String label) {
    UUID userId = UUID.randomUUID();
    String email = label + "-" + userId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users
          (id, email, normalized_email, full_name, status, actor_zone, organization_id)
        VALUES (?, ?, ?, ?, 'ACTIVE', 'CUSTOMER_ORGANIZATION', ?)
        """,
        userId,
        email,
        email,
        label,
        organizationId);
    return userId;
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
        .contains("ORG_ADMIN")
        .contains("INSPECTOR")
        .contains("MAINTENANCE_ENGINEER");
    assertThat(userColumns)
        .doesNotContain("provider_id", "activation_token_hash", "activation_expires_at");
  }
}
