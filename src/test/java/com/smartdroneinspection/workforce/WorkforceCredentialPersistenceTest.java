package com.smartdroneinspection.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import com.smartdroneinspection.workforce.domain.enums.WorkforceCredentialStatus;
import com.smartdroneinspection.workforce.repository.WorkforceCredentialRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Persistence contract for MF1 credential records and MF2/MF4 credential eligibility reads. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class WorkforceCredentialPersistenceTest {

  @PersistenceContext EntityManager entityManager;

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository users;
  @Autowired OrganizationRepository organizations;
  @Autowired InspectionRepository inspections;
  @Autowired WorkforceCredentialRepository credentials;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;

  private WorkforceCredentialTestFixture fixture;
  private WorkforceCredentialTestFixture.Data data;
  private UUID organizationId;
  private UUID assetCategoryId;
  private UUID subjectUserId;
  private UUID verifierUserId;

  @BeforeEach
  void setUp() {
    fixture = new WorkforceCredentialTestFixture(users, organizations, inspections, jdbcTemplate);
    data = fixture.create();

    AssetTestFixture.Data assetFixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = assetFixture.organizationId();
    assetCategoryId = assetFixture.categoryId();
    subjectUserId = createUser("inspector", UserRole.INSPECTOR);
    verifierUserId = createUser("reviewer", UserRole.ORG_MANAGER);
  }

  @Test
  void anActiveCredentialRoundTripsItsOwnershipValidityAndVerificationEvidence() {
    UUID credentialId = UUID.randomUUID();
    UUID evidenceId = UUID.randomUUID();
    UUID evidenceInspectionId = createEvidenceInspection();
    Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
    Instant expiresAt = Instant.parse("2027-01-01T00:00:00Z");
    Instant verifiedAt = Instant.parse("2026-02-01T12:30:00Z");
    insertEvidence(evidenceId, evidenceInspectionId);

    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials
            (id, organization_id, user_id, credential_type, issuer, credential_reference,
             issued_at, expires_at, status, evidence_id, verified_by_user_id, verified_at,
             verification_reason)
        VALUES (?, ?, ?, 'PILOT_QUALIFICATION', 'Civil Aviation Authority', 'LIC-007',
                ?, ?, 'ACTIVE', ?, ?, ?, 'Checked against submitted source evidence')
        """,
        credentialId,
        organizationId,
        subjectUserId,
        issuedAt.atOffset(ZoneOffset.UTC),
        expiresAt.atOffset(ZoneOffset.UTC),
        evidenceId,
        verifierUserId,
        verifiedAt.atOffset(ZoneOffset.UTC));
    entityManager.flush();
    entityManager.clear();

    WorkforceCredential credential = entityManager.find(WorkforceCredential.class, credentialId);

    assertThat(credential).isNotNull();
    assertThat(credential.getOrganizationId()).isEqualTo(organizationId);
    assertThat(credential.getUserId()).isEqualTo(subjectUserId);
    assertThat(credential.getCredentialType()).isEqualTo("PILOT_QUALIFICATION");
    assertThat(credential.getIssuer()).isEqualTo("Civil Aviation Authority");
    assertThat(credential.getCredentialReference()).isEqualTo("LIC-007");
    assertThat(credential.getIssuedAt()).isEqualTo(issuedAt);
    assertThat(credential.getExpiresAt()).isEqualTo(expiresAt);
    assertThat(credential.getStatus()).isEqualTo(WorkforceCredentialStatus.ACTIVE);
    assertThat(credential.getEvidenceId()).isEqualTo(evidenceId);
    assertThat(credential.getVerifiedByUserId()).isEqualTo(verifierUserId);
    assertThat(credential.getVerifiedAt()).isEqualTo(verifiedAt);
    assertThat(credential.getVerificationReason())
        .isEqualTo("Checked against submitted source evidence");
    assertThat(credential.getCreatedAt()).isNotNull();
    assertThat(credential.getUpdatedAt()).isNotNull();
    assertThat(credential.qualifiesAt(Instant.parse("2026-06-01T00:00:00Z"))).isTrue();
  }

  @Test
  void nullableCredentialMetadataRemainsNull() {
    UUID credentialId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials (id, organization_id, user_id, credential_type, status)
        VALUES (?, ?, ?, 'SPECIALIST_TRAINING', 'DRAFT')
        """,
        credentialId,
        organizationId,
        subjectUserId);
    entityManager.flush();
    entityManager.clear();

    WorkforceCredential credential = entityManager.find(WorkforceCredential.class, credentialId);

    assertThat(credential.getIssuer()).isNull();
    assertThat(credential.getIssuedAt()).isNull();
    assertThat(credential.getExpiresAt()).isNull();
    assertThat(credential.getEvidenceId()).isNull();
    assertThat(credential.getVerifiedByUserId()).isNull();
    assertThat(credential.getVerifiedAt()).isNull();
    assertThat(credential.getVerificationReason()).isNull();
  }

  @Test
  void roundTripsEveryColumnDefinedByTheTargetSchema() {
    UUID evidenceId = fixture.createEvidence(data.organizationId(), "persisted");
    Instant issuedAt = Instant.now().minus(200, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
    Instant expiresAt = Instant.now().plus(200, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
    Instant verifiedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    WorkforceCredential saved =
        credentials.saveAndFlush(
            new WorkforceCredential(
                data.organizationId(),
                data.subjectId(),
                "PILOT_LICENSE",
                "Aviation Authority",
                "REF-12345",
                issuedAt,
                expiresAt,
                evidenceId,
                data.reviewerId(),
                verifiedAt,
                "Scan matches the certificate number."));
    entityManager.flush();
    entityManager.clear();

    WorkforceCredential reloaded =
        credentials.findInOrganization(saved.getId(), data.organizationId()).orElseThrow();
    assertThat(reloaded.getOrganizationId()).isEqualTo(data.organizationId());
    assertThat(reloaded.getUserId()).isEqualTo(data.subjectId());
    assertThat(reloaded.getCredentialType()).isEqualTo("PILOT_LICENSE");
    assertThat(reloaded.getIssuer()).isEqualTo("Aviation Authority");
    assertThat(reloaded.getCredentialReference()).isEqualTo("REF-12345");
    assertThat(reloaded.getIssuedAt()).isEqualTo(issuedAt);
    assertThat(reloaded.getExpiresAt()).isEqualTo(expiresAt);
    assertThat(reloaded.getEvidenceId()).isEqualTo(evidenceId);
    assertThat(reloaded.getVerifiedByUserId()).isEqualTo(data.reviewerId());
    assertThat(reloaded.getVerifiedAt()).isEqualTo(verifiedAt);
    assertThat(reloaded.getVerificationReason()).isEqualTo("Scan matches the certificate number.");
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();
  }

  @Test
  void findInOrganizationHidesAnotherTenantsCredential() {
    UUID foreignCredentialId = persist(data.foreignSubjectId(), data.otherOrganizationId());

    assertThat(credentials.findInOrganization(foreignCredentialId, data.organizationId()))
        .isEmpty();
    assertThat(credentials.findInOrganization(foreignCredentialId, data.otherOrganizationId()))
        .isPresent();
  }

  @Test
  void listByOrganizationReturnsOnlyThatTenantsCredentials() {
    persist(data.subjectId(), data.organizationId());
    UUID foreignCredentialId = persist(data.foreignSubjectId(), data.otherOrganizationId());

    assertThat(credentials.listByOrganization(data.organizationId()))
        .allSatisfy(
            credential ->
                assertThat(credential.getOrganizationId()).isEqualTo(data.organizationId()));
    assertThat(credentials.listByOrganization(data.organizationId()))
        .noneSatisfy(credential -> assertThat(credential.getId()).isEqualTo(foreignCredentialId));
  }

  @Test
  void derivesExpiryStatesFromDatesWithoutClaimingExternalVerification() {
    Instant now = Instant.now();

    assertThat(statusAt("PENDING_REVIEW", now.plus(400, ChronoUnit.DAYS)))
        .isEqualTo(WorkforceCredential.Status.PENDING_REVIEW);
    assertThat(statusAt("ACTIVE", now.plus(400, ChronoUnit.DAYS)))
        .isEqualTo(WorkforceCredential.Status.ACTIVE);
    assertThat(statusAt("ACTIVE", now.plus(5, ChronoUnit.DAYS)))
        .isEqualTo(WorkforceCredential.Status.EXPIRING_SOON);
    assertThat(statusAt("ACTIVE", now.minus(1, ChronoUnit.DAYS)))
        .isEqualTo(WorkforceCredential.Status.EXPIRED);
    assertThat(statusAt("REJECTED", now.plus(400, ChronoUnit.DAYS)))
        .isEqualTo(WorkforceCredential.Status.REJECTED);
    assertThat(statusAt("SUSPENDED", now.plus(400, ChronoUnit.DAYS)))
        .isEqualTo(WorkforceCredential.Status.SUSPENDED);
    assertThat(statusAt("ACTIVE", null)).isEqualTo(WorkforceCredential.Status.ACTIVE);
  }

  @Test
  void reviewIsStoredAgainstTheReviewingActorAndTime() {
    UUID credentialId = persist(data.subjectId(), data.organizationId());
    Instant verifiedAt = Instant.now().minusSeconds(60);

    credentials
        .findById(credentialId)
        .orElseThrow()
        .review(
            WorkforceCredential.Status.ACTIVE,
            data.reviewerId(),
            verifiedAt,
            "Checked against the register.");
    entityManager.flush();
    entityManager.clear();

    var row =
        jdbcTemplate.queryForList(
            "SELECT status, verified_by_user_id, verified_at, verification_reason, updated_at"
                + " FROM workforce_credentials WHERE id = ?",
            credentialId);
    assertThat(row).hasSize(1);
    assertThat(row.getFirst().get("status")).isEqualTo("ACTIVE");
    assertThat(row.getFirst().get("verified_by_user_id")).isEqualTo(data.reviewerId());
    assertThat(row.getFirst().get("verified_at")).isNotNull();
    assertThat(row.getFirst().get("verification_reason"))
        .isEqualTo("Checked against the register.");
    assertThat(row.getFirst().get("updated_at")).isNotNull();
  }

  private WorkforceCredential.Status statusAt(String persistedStatus, Instant expiresAt) {
    UUID credentialId =
        persist(data.subjectId(), data.organizationId(), persistedStatus, expiresAt);
    entityManager.clear();
    return credentials.findById(credentialId).orElseThrow().currentStatus(Instant.now());
  }

  private UUID persist(UUID subjectId, UUID credentialOrganizationId) {
    return persist(
        subjectId,
        credentialOrganizationId,
        "PENDING_REVIEW",
        Instant.now().plus(400, ChronoUnit.DAYS));
  }

  private UUID persist(
      UUID subjectId, UUID credentialOrganizationId, String status, Instant expiresAt) {
    UUID credentialId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials (id, organization_id, user_id, credential_type, issuer,
                                          issued_at, expires_at, status)
        VALUES (?, ?, ?, 'QUALIFICATION', 'Authority', ?, ?, ?)
        """,
        credentialId,
        credentialOrganizationId,
        subjectId,
        java.sql.Timestamp.from(Instant.now().minus(200, ChronoUnit.DAYS)),
        expiresAt == null ? null : java.sql.Timestamp.from(expiresAt),
        status);
    return credentialId;
  }

  private UUID createEvidenceInspection() {
    UUID assetId =
        assets
            .saveAndFlush(
                new Asset(
                    organizationId,
                    assetCategoryId,
                    "ASSET-" + UUID.randomUUID(),
                    "Credential Evidence Asset",
                    null,
                    null,
                    null,
                    null,
                    null,
                    verifierUserId))
            .getId();
    UUID inspectionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO inspections (id, organization_id, asset_id, status, objective)"
            + " VALUES (?, ?, ?, 'ASSIGNED', 'Credential evidence inspection')",
        inspectionId,
        organizationId,
        assetId);
    return inspectionId;
  }

  private void insertEvidence(UUID evidenceId, UUID inspectionId) {
    jdbcTemplate.update(
        """
        INSERT INTO evidence
            (id, inspection_id, file_name, content_type,
             size_bytes, object_key)
        VALUES (?, ?, 'qualification.pdf', 'application/pdf', 128,
                ?)
        """,
        evidenceId,
        inspectionId,
        "credential-evidence/" + evidenceId);
  }

  private UUID createUser(String label, UserRole role) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }
}
