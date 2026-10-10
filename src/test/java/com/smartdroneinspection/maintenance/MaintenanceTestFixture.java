package com.smartdroneinspection.maintenance;

import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MF4 fixtures: one organization with an owner/budget approver, a team lead, a report author, an
 * independent accepting reviewer and a credential-held engineer, plus a second organization used to
 * prove cross-tenant denial.
 */
public final class MaintenanceTestFixture {

  private final UserRepository users;
  private final JdbcTemplate jdbcTemplate;

  public MaintenanceTestFixture(UserRepository users, JdbcTemplate jdbcTemplate) {
    this.users = users;
    this.jdbcTemplate = jdbcTemplate;
  }

  public Data create() {
    UUID organizationId = createOrganization("MF4 primary");
    UUID otherOrganizationId = createOrganization("MF4 other");

    User owner = saveUser("mf4-owner", organizationId, UserRole.ORG_ADMIN);
    User approver = saveUser("mf4-approver", organizationId, UserRole.ORG_ADMIN);
    User lead = saveUser("mf4-lead", organizationId, UserRole.MAINTENANCE_ENGINEER);
    User reportAuthor = saveUser("mf4-author", organizationId, UserRole.MAINTENANCE_ENGINEER);
    User reviewer = saveUser("mf4-reviewer", organizationId, UserRole.ORG_ADMIN);
    User engineer = saveUser("mf4-engineer", organizationId, UserRole.MAINTENANCE_ENGINEER);
    User outsider = saveUser("mf4-outsider", otherOrganizationId, UserRole.ORG_ADMIN);

    UUID assetId = createAsset(organizationId);
    SourceData source = createPublishedReportVersion(organizationId, assetId, owner.getId());
    UUID findingId =
        createVerifiedFinding(source.inspectionId(), source.versionId(), owner.getId());

    return new Data(
        organizationId,
        otherOrganizationId,
        assetId,
        source.versionId(),
        findingId,
        owner.getId(),
        approver.getId(),
        lead.getId(),
        reportAuthor.getId(),
        reviewer.getId(),
        engineer.getId(),
        outsider.getId());
  }

  /** Seeds a published report version that MF4 work orders point at. */
  private SourceData createPublishedReportVersion(
      UUID organizationId, UUID assetId, UUID authorUserId) {
    UUID categoryId =
        jdbcTemplate.queryForObject(
            "SELECT category_id FROM assets WHERE id = ?", UUID.class, assetId);
    UUID checklistTemplateId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO checklist_templates (id, template_key, version_number, asset_category_id, name,
                                        status, created_by_user_id, published_at, created_at, updated_at, row_version)
        VALUES (?, ?, 1, ?, 'MF4 Source Checklist', 'ACTIVE', ?, CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        checklistTemplateId,
        ("MF4-" + UUID.randomUUID()).toUpperCase(Locale.ROOT),
        categoryId,
        authorUserId);

    UUID droneId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO drones (id, organization_id, serial_number, model, manufacturer, serviceability,
                            created_at, updated_at, row_version)
        VALUES (?, ?, ?, 'MF4 fixture drone', 'Test Manufacturer', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        droneId,
        organizationId,
        "DRN-" + UUID.randomUUID());

    UUID inspectionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, asset_id, status, created_at, updated_at, row_version,
                                 organization_id, objective, scope, component_scope, acceptance_criteria,
                                 inspector_id, drone_id)
        VALUES (?, ?, 'REPAIR_PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                ?, 'MF4 source inspection', '{"method":"visual"}', '[]', '{"checks":[]}', ?, ?)
        """,
        inspectionId,
        assetId,
        organizationId,
        authorUserId,
        droneId);

    UUID reportId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspection_reports (id, inspection_id, author_user_id, status, current_version_number,
                                        created_at, updated_at, row_version)
        VALUES (?, ?, ?, 'PUBLISHED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        reportId,
        inspectionId,
        authorUserId);

    UUID versionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspection_report_versions (id, inspection_report_id, version_no, status, author_user_id,
                                                content_snapshot, created_at, published_at)
        VALUES (?, ?, 1, 'PUBLISHED', ?, '{"summary":"source report"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        versionId,
        reportId,
        authorUserId);
    return new SourceData(inspectionId, versionId);
  }

  /** A human-confirmed repair-required finding, which is the only kind MF4 may act on. */
  private UUID createVerifiedFinding(UUID inspectionId, UUID reportVersionId, UUID authorUserId) {
    UUID findingId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO verified_findings (id, inspection_id, created_by_user_id, source, finding_code,
                                       defect_label, severity, location_description, technical_notes,
                                       status, created_at, updated_at, row_version, description,
                                       component, observed_condition, decision, decided_by_user_id,
                                       decided_at, repair_required)
        VALUES (?, ?, ?, 'MANUAL', ?, 'Corrosion', 'HIGH', 'Main span', 'Corrosion observed',
                'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'Corrosion on main span',
                'Support', 'Corrosion observed', 'CONFIRMED', ?, CURRENT_TIMESTAMP, TRUE)
        """,
        findingId,
        inspectionId,
        authorUserId,
        "F-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
        authorUserId);
    return findingId;
  }

  private record SourceData(UUID inspectionId, UUID versionId) {}

  private UUID createAsset(UUID organizationId) {
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (id, code, name, description, active)
        VALUES (?, ?, 'Bridge', 'MF4 fixture category', TRUE)
        """,
        categoryId,
        ("CAT" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT));
    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO assets (id, organization_id, category_id, asset_code, name, location, status,
                            created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Main span', '{"label":"District 1"}', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        assetId,
        organizationId,
        categoryId,
        ("AST" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT));
    return assetId;
  }

  private UUID createOrganization(String label) {
    UUID organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        organizationId,
        label + " " + organizationId,
        label + " " + organizationId,
        "ORG-" + organizationId);
    return organizationId;
  }

  private User saveUser(String label, UUID organizationId, UserRole role) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    user.addRole(role);
    return users.saveAndFlush(user);
  }

  /** Gives a user a credential record in a given status, to exercise the MF4-04 gate. */
  public void grantCredential(UUID organizationId, UUID userId, String status, Instant expiredAt) {
    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials (id, organization_id, user_id, credential_type, status,
                                          expires_at, created_at, updated_at, row_version)
        VALUES (?, ?, ?, 'ELECTRICAL_SAFETY', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        UUID.randomUUID(),
        organizationId,
        userId,
        status,
        expiredAt == null ? null : Timestamp.from(expiredAt));
  }

  public record Data(
      UUID organizationId,
      UUID otherOrganizationId,
      UUID assetId,
      UUID reportVersionId,
      UUID findingId,
      UUID ownerId,
      UUID approverId,
      UUID leadId,
      UUID reportAuthorId,
      UUID reviewerId,
      UUID engineerId,
      UUID outsiderId) {}
}
