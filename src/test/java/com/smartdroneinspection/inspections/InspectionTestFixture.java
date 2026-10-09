package com.smartdroneinspection.inspections;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MF3 fixtures: one organization with an assigned Inspector and a qualified ORG_ADMIN reviewer, a
 * second organization for cross-tenant denial, and an inspection already past field work.
 */
public final class InspectionTestFixture {

  private final UserRepository users;
  private final InspectionRepository inspections;
  private final JdbcTemplate jdbcTemplate;

  public InspectionTestFixture(
      UserRepository users, InspectionRepository inspections, JdbcTemplate jdbcTemplate) {
    this.users = users;
    this.inspections = inspections;
    this.jdbcTemplate = jdbcTemplate;
  }

  public Data create() {
    UUID organizationId = createOrganization("MF3 primary");
    UUID otherOrganizationId = createOrganization("MF3 other");

    User inspector = saveUser("mf3-inspector", organizationId, UserRole.INSPECTOR);
    User reviewer = saveUser("mf3-reviewer", organizationId, UserRole.ORG_ADMIN);
    // An INSPECTOR who is not the assignee, to prove assignment scope rather than role is enforced.
    User otherInspector = saveUser("mf3-other-inspector", organizationId, UserRole.INSPECTOR);
    User outsider = saveUser("mf3-outsider", otherOrganizationId, UserRole.ORG_ADMIN);

    UUID assetId = createAsset(organizationId);
    Inspection inspection =
        inspections.saveAndFlush(
            new Inspection(
                organizationId,
                assetId,
                null,
                null,
                inspector.getId(),
                null,
                "mf3-" + UUID.randomUUID(),
                "Visual inspection of the main span",
                "{\"method\":\"visual\"}",
                "[\"main span\"]",
                "{\"criteria\":\"no visible structural damage\"}",
                null,
                null));
    // MF3 starts from an ended field session.
    advanceToFieldCompleted(inspection);
    return new Data(
        organizationId,
        otherOrganizationId,
        assetId,
        inspection.getId(),
        inspector.getId(),
        reviewer.getId(),
        otherInspector.getId(),
        outsider.getId());
  }

  private void advanceToFieldCompleted(Inspection inspection) {
    jdbcTemplate.update(
        "UPDATE inspections SET status = ? WHERE id = ?",
        InspectionStatus.FIELD_COMPLETED.name(),
        inspection.getId());
  }

  private UUID createAsset(UUID organizationId) {
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (id, code, name, description, active)
        VALUES (?, ?, 'Bridge', 'MF3 fixture category', TRUE)
        """,
        categoryId,
        upperCode("CAT"));
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
        upperCode("AST"));
    return assetId;
  }

  /** Codes are stored normalized: the schema rejects anything that is not upper case. */
  private static String upperCode(String prefix) {
    return (prefix + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
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

  public record Data(
      UUID organizationId,
      UUID otherOrganizationId,
      UUID assetId,
      UUID inspectionId,
      UUID inspectorId,
      UUID reviewerId,
      UUID otherInspectorId,
      UUID outsiderId) {}
}
