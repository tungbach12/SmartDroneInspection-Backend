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
  void createsTheTargetColumnContractAcrossAllFortyOneTables() {
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
                Set.of("organization_id", "normalized_email", "full_name", "auth_version")),
            Map.entry("user_roles", Set.of("user_id", "role")),
            Map.entry(
                "auth_sessions", Set.of("user_id", "client_type", "expires_at", "revoked_at")),
            Map.entry(
                "refresh_tokens",
                Set.of("session_id", "token_hash", "issued_at", "expires_at", "revoked_at")),
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
            Map.entry("asset_categories", Set.of("organization_id", "code", "name", "active")),
            Map.entry(
                "checklist_templates",
                Set.of(
                    "template_key",
                    "context",
                    "active_from",
                    "active_until",
                    "created_by_user_id")),
            Map.entry(
                "checklist_items",
                Set.of("template_id", "item_code", "prompt", "required", "evidence_requirement")),
            Map.entry(
                "assets",
                Set.of(
                    "organization_id",
                    "asset_code",
                    "category_id",
                    "asset_type",
                    "location",
                    "technical_profile",
                    "status")),
            Map.entry(
                "asset_documents",
                Set.of(
                    "asset_id",
                    "document_type",
                    "object_key",
                    "checksum_sha256",
                    "uploaded_by_user_id")),
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
                "inspection_schedules",
                Set.of(
                    "asset_id",
                    "cadence_unit",
                    "cadence_interval",
                    "next_due_at",
                    "scope_defaults",
                    "status")),
            Map.entry(
                "inspections",
                Set.of(
                    "organization_id",
                    "asset_id",
                    "schedule_id",
                    "objective",
                    "scope",
                    "planned_start_at",
                    "inspector_id",
                    "drone_id",
                    "status")),
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
                    "permit_snapshot_ids",
                    "credential_snapshot_ids",
                    "drone_document_snapshot_ids",
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
                    "organization_id",
                    "inspection_id",
                    "field_session_id",
                    "maintenance_work_order_id",
                    "maintenance_task_id",
                    "kind",
                    "capture_metadata",
                    "object_key",
                    "checksum_sha256",
                    "uploaded_by_user_id")),
            Map.entry(
                "evidence_quality_decisions",
                Set.of("inspection_id", "decision", "decided_by_user_id", "decided_at")),
            Map.entry(
                "ai_finding_candidates",
                Set.of(
                    "inspection_id", "evidence_id", "model_provider", "model_version", "status")),
            Map.entry(
                "verified_findings",
                Set.of(
                    "inspection_id",
                    "component",
                    "description",
                    "severity",
                    "decision",
                    "repair_required")),
            Map.entry(
                "inspection_reports",
                Set.of("inspection_id", "status", "current_version_number", "author_user_id")),
            Map.entry(
                "inspection_report_versions",
                Set.of(
                    "inspection_report_id",
                    "version_no",
                    "status",
                    "author_user_id",
                    "content_snapshot")),
            Map.entry("report_version_evidence", Set.of("report_version_id", "evidence_id")),
            Map.entry("report_version_findings", Set.of("report_version_id", "finding_id")),
            Map.entry(
                "maintenance_work_orders",
                Set.of(
                    "organization_id",
                    "source_report_version_id",
                    "source_finding_id",
                    "status",
                    "due_at")),
            Map.entry(
                "maintenance_tasks",
                Set.of(
                    "work_order_id", "task_number", "name", "status", "assigned_engineer_user_id")),
            Map.entry(
                "maintenance_team_members",
                Set.of(
                    "work_order_id",
                    "engineer_user_id",
                    "member_role",
                    "effective_from",
                    "active")),
            Map.entry(
                "maintenance_estimate_versions",
                Set.of("work_order_id", "version_no", "status", "currency", "baseline_total")),
            Map.entry(
                "maintenance_cost_lines",
                Set.of(
                    "work_order_id",
                    "line_kind",
                    "description",
                    "quantity",
                    "unit_rate",
                    "amount",
                    "currency")),
            Map.entry(
                "maintenance_change_orders",
                Set.of("work_order_id", "change_number", "reason", "proposed_delta", "status")),
            Map.entry(
                "maintenance_work_logs",
                Set.of(
                    "work_order_id",
                    "task_id",
                    "engineer_user_id",
                    "started_at",
                    "hours",
                    "status")),
            Map.entry(
                "maintenance_report_versions",
                Set.of(
                    "work_order_id", "version_no", "status", "author_user_id", "content_snapshot")),
            Map.entry(
                "maintenance_acceptance_decisions",
                Set.of(
                    "work_order_id",
                    "report_version_id",
                    "reviewer_user_id",
                    "decision",
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

    assertThat(targetColumns).hasSize(41);
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
          (id, organization_id, category_id, asset_code, asset_type, name, location, status)
        VALUES (?, ?, ?, ?, 'TEST', 'Test asset', '{"label":"Test location"}'::jsonb, 'ACTIVE')
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
  void enforcesTargetEvidenceWorkOrderAndTaskForeignKeys() {
    String workOrderForeignKey =
        jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'fk_evidence_target_work_order'",
            String.class);
    String taskForeignKey =
        jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'fk_evidence_target_task'",
            String.class);
    String parentCheck =
        jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_evidence_target_parent'",
            String.class);

    assertThat(workOrderForeignKey)
        .contains("FOREIGN KEY (maintenance_work_order_id)")
        .contains("maintenance_work_orders")
        .contains("ON DELETE RESTRICT");
    assertThat(taskForeignKey)
        .contains("FOREIGN KEY (maintenance_task_id)")
        .contains("maintenance_tasks")
        .contains("ON DELETE RESTRICT");
    assertThat(parentCheck)
        .contains("inspection_id")
        .contains("field_session_id")
        .contains("maintenance_work_order_id")
        .contains("maintenance_task_id")
        .doesNotContain("maintenance_work_log_id");
  }

  @Test
  void rejectsEvidenceWithMissingOrOrphanedTargetParents() {
    UUID uploaderId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, normalized_email, full_name, status, actor_zone, organization_id) "
            + "VALUES (?, ?, ?, ?, 'ACTIVE', 'PLATFORM', NULL)",
        uploaderId,
        uploaderId + "@example.test",
        uploaderId + "@example.test",
        "Evidence fixture");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO evidence
                      (id, uploaded_by_user_id, evidence_kind, file_name, content_type, size_bytes,
                       checksum_sha256, object_key, source, upload_status)
                    VALUES (?, ?, 'OTHER', 'file', 'text/plain', 1, ?, ?, 'IMPORTED', 'AVAILABLE')
                    """,
                    UUID.randomUUID(),
                    uploaderId,
                    "a".repeat(64),
                    "no-parent-" + UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO evidence
                      (id, uploaded_by_user_id, evidence_kind, file_name, content_type, size_bytes,
                       checksum_sha256, object_key, source, upload_status, maintenance_work_order_id)
                    VALUES (?, ?, 'OTHER', 'file', 'text/plain', 1, ?, ?, 'IMPORTED', 'AVAILABLE', ?)
                    """,
                    UUID.randomUUID(),
                    uploaderId,
                    "b".repeat(64),
                    "orphan-work-order-" + UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO evidence
                      (id, uploaded_by_user_id, evidence_kind, file_name, content_type, size_bytes,
                       checksum_sha256, object_key, source, upload_status, maintenance_task_id)
                    VALUES (?, ?, 'OTHER', 'file', 'text/plain', 1, ?, ?, 'IMPORTED', 'AVAILABLE', ?)
                    """,
                    UUID.randomUUID(),
                    uploaderId,
                    "c".repeat(64),
                    "orphan-task-" + UUID.randomUUID(),
                    UUID.randomUUID()))
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
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_user_roles_role'",
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
