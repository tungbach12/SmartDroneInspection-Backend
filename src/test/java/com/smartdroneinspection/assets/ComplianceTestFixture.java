package com.smartdroneinspection.assets;

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
 * MF1-07 permit fixtures: an entitled tenant that holds an asset and evidence, a second entitled
 * tenant used for cross-tenant denial, and a third tenant with no subscription.
 */
final class ComplianceTestFixture {

  private final UserRepository users;
  private final OrganizationRepository organizations;
  private final InspectionRepository inspections;
  private final JdbcTemplate jdbcTemplate;

  ComplianceTestFixture(
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
    UUID organizationId = saveOrganization("Compliance tenant").getId();
    UUID otherOrganizationId = saveOrganization("Compliance other tenant").getId();
    UUID unentitledOrganizationId = saveOrganization("Compliance unentitled tenant").getId();

    UUID adminId = saveUser("compliance-admin", organizationId, UserRole.ORG_ADMIN).getId();
    UUID inspectorId = saveUser("compliance-inspector", organizationId, UserRole.INSPECTOR).getId();
    UUID foreignAdminId =
        saveUser("compliance-foreign-admin", otherOrganizationId, UserRole.ORG_ADMIN).getId();
    UUID unentitledAdminId =
        saveUser("compliance-unentitled-admin", unentitledOrganizationId, UserRole.ORG_ADMIN)
            .getId();

    grantEntitlement(organizationId);
    grantEntitlement(otherOrganizationId);

    return new Data(
        organizationId,
        otherOrganizationId,
        unentitledOrganizationId,
        adminId,
        inspectorId,
        foreignAdminId,
        unentitledAdminId,
        createAsset(organizationId, "owned"),
        createAsset(otherOrganizationId, "foreign"),
        createEvidence(organizationId, "owned"),
        createEvidence(otherOrganizationId, "foreign"));
  }

  UUID createAsset(UUID organizationId, String label) {
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_categories (id, code, name, description, active)
        VALUES (?, ?, 'Bridge', 'Compliance fixture category', TRUE)
        """,
        categoryId,
        upperCode("CAT"));

    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO assets (id, organization_id, category_id, asset_code, name, location, status,
                            created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Compliance span', '{"label":"District 9"}', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        assetId,
        organizationId,
        categoryId,
        upperCode("AST"));
    return assetId;
  }

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
        INSERT INTO evidence (id, organization_id, inspection_id, evidence_kind,
                              file_name, content_type, size_bytes, object_key,
                              upload_status, created_at)
        VALUES (?, ?, ?, 'INSPECTION', ?, 'image/png', 1, ?, 'AVAILABLE',
                CURRENT_TIMESTAMP)
        """,
        evidenceId,
        organizationId,
        inspectionId,
        "compliance-" + label + ".png",
        "compliance-fixture/" + label + "/" + evidenceId);
    return evidenceId;
  }

  UUID createInspection(UUID organizationId) {
    UUID assetId = createAsset(organizationId, "inspection");
    return inspections
        .saveAndFlush(
            new Inspection(
                organizationId,
                assetId,
                null,
                null,
                null,
                null,
                "mf1-" + UUID.randomUUID(),
                "Compliance fixture inspection",
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
      UUID adminId,
      UUID inspectorId,
      UUID foreignAdminId,
      UUID unentitledAdminId,
      UUID assetId,
      UUID foreignAssetId,
      UUID evidenceId,
      UUID foreignEvidenceId) {}
}
