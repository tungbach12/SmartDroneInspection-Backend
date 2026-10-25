package com.smartdroneinspection.workforce;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MF1 credential fixtures: an entitled tenant with an ORG_ADMIN reviewer and an Inspector subject,
 * a second tenant for cross-tenant denial, and a third tenant with no subscription.
 */
final class WorkforceCredentialTestFixture {

  private final UserRepository users;
  private final OrganizationRepository organizations;
  private final InspectionRepository inspections;
  private final JdbcTemplate jdbcTemplate;

  WorkforceCredentialTestFixture(
      UserRepository users,
      OrganizationRepository organizations,
      InspectionRepository inspections,
      JdbcTemplate jdbcTemplate) {
    this.users = users;
    this.organizations = organizations;
    this.inspections = inspections;
    this.jdbcTemplate = jdbcTemplate;
  }

  Data create() {
    UUID organizationId = saveOrganization("Credential tenant").getId();
    UUID otherOrganizationId = saveOrganization("Credential other tenant").getId();
    UUID unentitledOrganizationId = saveOrganization("Credential unentitled tenant").getId();

    UUID reviewerId = saveUser("credential-reviewer", organizationId, UserRole.ORG_ADMIN).getId();
    UUID subjectId = saveUser("credential-subject", organizationId, UserRole.INSPECTOR).getId();
    UUID foreignReviewerId =
        saveUser("credential-foreign-reviewer", otherOrganizationId, UserRole.ORG_ADMIN).getId();
    UUID foreignSubjectId =
        saveUser("credential-foreign-subject", otherOrganizationId, UserRole.INSPECTOR).getId();
    UUID unentitledReviewerId =
        saveUser("credential-unentitled-reviewer", unentitledOrganizationId, UserRole.ORG_ADMIN)
            .getId();

    grantEntitlement(organizationId);
    grantEntitlement(otherOrganizationId);

    return new Data(
        organizationId,
        otherOrganizationId,
        unentitledOrganizationId,
        reviewerId,
        subjectId,
        foreignReviewerId,
        foreignSubjectId,
        unentitledReviewerId);
  }

  /** Evidence needs a parent row, so each tenant gets a coherent inspection of its own. */
  UUID createEvidence(UUID organizationId, String label) {
    UUID uploaderId =
        users.findWorkforceByOrganizationId(organizationId).stream()
            .findFirst()
            .orElseThrow()
            .getId();
    UUID inspectionId = createInspection(organizationId);
    UUID evidenceId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO evidence (id, inspection_id,
                              file_name, content_type, size_bytes, object_key,
                              created_at)
        VALUES (?, ?, ?, 'image/png', 1, ?,
                CURRENT_TIMESTAMP)
        """,
        evidenceId,
        inspectionId,
        "credential-" + label + ".png",
        "credential-fixture/" + label + "/" + evidenceId);
    return evidenceId;
  }

  UUID createInspection(UUID organizationId) {
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (id, code, name, description, active)
        VALUES (?, ?, 'Bridge', 'Credential fixture category', TRUE)
        """,
        categoryId,
        upperCode("CAT"));

    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO assets (id, organization_id, category_id, asset_code, name, location, status,
                            created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Credential span', '{"label":"District 7"}', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        assetId,
        organizationId,
        categoryId,
        upperCode("AST"));

    return inspections
        .saveAndFlush(
            new Inspection(
                organizationId,
                assetId,
                null,
                null,
                null,
                "Credential fixture inspection",
                null,
                null,
                null,
                null,
                null))
        .getId();
  }

  void grantEntitlement(UUID organizationId) {
    jdbcTemplate.update(
        """
        INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at)
        VALUES (?, 'ENTERPRISE', 1, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')
        """,
        organizationId);
  }

  /** Codes are stored normalized: the schema rejects anything that is not upper case. */
  private static String upperCode(String prefix) {
    return (prefix + UUID.randomUUID()).toUpperCase(Locale.ROOT);
  }

  private Organization saveOrganization(String name) {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    return organizations.saveAndFlush(new Organization(name, name + "-" + suffix, "Test fixture"));
  }

  private User saveUser(String prefix, UUID organizationId, UserRole role) {
    User user =
        new User(
            prefix + "-" + UUID.randomUUID() + "@example.test",
            prefix,
            "{argon2}fixture-hash",
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    user.addRole(role);
    return users.saveAndFlush(user);
  }

  record Data(
      UUID organizationId,
      UUID otherOrganizationId,
      UUID unentitledOrganizationId,
      UUID reviewerId,
      UUID subjectId,
      UUID foreignReviewerId,
      UUID foreignSubjectId,
      UUID unentitledReviewerId) {}
}
