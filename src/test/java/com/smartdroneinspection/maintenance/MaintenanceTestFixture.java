package com.smartdroneinspection.maintenance;

import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
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

    User owner = saveUser("mf4-owner", organizationId, UserRole.CUSTOMER);
    User approver = saveUser("mf4-approver", organizationId, UserRole.CUSTOMER);
    User lead = saveUser("mf4-lead", organizationId, UserRole.MAINTENANCE_ENGINEER);
    User reportAuthor = saveUser("mf4-author", organizationId, UserRole.MAINTENANCE_ENGINEER);
    User reviewer = saveUser("mf4-reviewer", organizationId, UserRole.CUSTOMER);
    User engineer = saveUser("mf4-engineer", organizationId, UserRole.MAINTENANCE_ENGINEER);
    User outsider = saveUser("mf4-outsider", otherOrganizationId, UserRole.CUSTOMER);

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
                                 organization_id, inspector_id, drone_id)
        VALUES (?, ?, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                ?, ?, ?)
        """,
        inspectionId,
        assetId,
        organizationId,
        authorUserId,
        droneId);

    UUID versionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspection_reports (id, inspection_id, version_no, status,
                                        created_at, published_at)
        VALUES (?, ?, 1, 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        versionId,
        inspectionId);
    return new SourceData(inspectionId, versionId);
  }

  /** A human-confirmed repair-required finding, which is the only kind MF4 may act on. */
  private UUID createVerifiedFinding(UUID inspectionId, UUID reportVersionId, UUID authorUserId) {
    UUID findingId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO findings (id, inspection_id, finding_code,
                              defect_label, severity, technical_notes,
                              status, created_at,
                              forwarded_to_maintenance_at)
        VALUES (?, ?, ?, 'Corrosion', 'HIGH', 'Corrosion observed',
                'CONFIRMED', CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP)
        """,
        findingId,
        inspectionId,
        "F-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
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

  public UUID createAdditionalFinding(UUID userId, UUID referenceFindingId) {
    UUID findingId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO findings (
            id, inspection_id, evidence_id, finding_code,
            defect_label, severity, bounding_box, technical_notes,
            recommended_action, status, created_at,
            forwarded_to_maintenance_at)
        SELECT ?, inspection_id, NULL, ?, 'Additional corrosion', 'MEDIUM',
               NULL, 'Additional corrosion observed', NULL, 'CONFIRMED',
               CURRENT_TIMESTAMP,
               CURRENT_TIMESTAMP
          FROM findings WHERE id = ?
        """,
        findingId,
        ("F-" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase(Locale.ROOT),
        referenceFindingId);
    return findingId;
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
