package com.smartdroneinspection.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import com.smartdroneinspection.workforce.domain.enums.WorkforceCredentialStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Persistence contract for workforce credentials using the V25 schema. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class WorkforceCredentialPersistenceTest {

  @PersistenceContext EntityManager entityManager;

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;

  UUID organizationId;
  UUID assetCategoryId;
  UUID subjectUserId;
  UUID verifierUserId;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    assetCategoryId = fixture.categoryId();
    subjectUserId = createUser("inspector", UserRole.INSPECTOR);
    verifierUserId = createUser("reviewer", UserRole.ORG_ADMIN);
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
  }

  @Test
  void nullableCredentialMetadataRemainsNull() {
    UUID credentialId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials
            (id, organization_id, user_id, credential_type, status)
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
        """
        INSERT INTO inspections (id, organization_id, asset_id, status, objective)
        VALUES (?, ?, ?, 'ASSIGNED', 'Credential evidence inspection')
        """,
        inspectionId,
        organizationId,
        assetId);
    return inspectionId;
  }

  private void insertEvidence(UUID evidenceId, UUID inspectionId) {
    jdbcTemplate.update(
        """
        INSERT INTO evidence
            (id, inspection_id, uploaded_by_user_id, evidence_kind, file_name, content_type,
             size_bytes, checksum_sha256, object_key, source, upload_status)
        VALUES (?, ?, ?, 'INSPECTION', 'qualification.pdf', 'application/pdf', 128,
                '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
                ?, 'WEB_UPLOAD', 'AVAILABLE')
        """,
        evidenceId,
        inspectionId,
        subjectUserId,
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
