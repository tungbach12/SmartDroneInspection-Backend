package com.smartdroneinspection.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.workforce.credential.CredentialStatus;
import com.smartdroneinspection.workforce.credential.WorkforceCredentialAccess;
import com.smartdroneinspection.workforce.credential.WorkforceCredentialSummary;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Organization- and owner-scoped credential read contract. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class WorkforceCredentialAccessIntegrationTest {

  @Autowired WorkforceCredentialAccess credentials;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired JdbcTemplate jdbcTemplate;

  private UUID organizationId;
  private UUID otherOrganizationId;
  private UUID reviewerId;
  private UUID sameOrganizationUserId;
  private UUID otherOrganizationUserId;
  private UUID reviewerCredentialId;
  private UUID sameOrganizationCredentialId;
  private UUID otherOrganizationCredentialId;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    otherOrganizationId = fixture.otherOrganizationId();
    reviewerId = createUser("reviewer", organizationId);
    sameOrganizationUserId = createUser("same-org", organizationId);
    otherOrganizationUserId = createUser("other-org", otherOrganizationId);
    reviewerCredentialId = insertCredential(reviewerId, organizationId);
    sameOrganizationCredentialId = insertCredential(sameOrganizationUserId, organizationId);
    otherOrganizationCredentialId = insertCredential(otherOrganizationUserId, otherOrganizationId);
  }

  @Test
  void returnsCredentialSummaryOnlyForExactCredentialOrganizationAndOwnerTuple() {
    Optional<WorkforceCredentialSummary> result =
        credentials.findByIdAndOrganizationIdAndUserId(
            reviewerCredentialId, organizationId, reviewerId);

    assertThat(result)
        .contains(
            new WorkforceCredentialSummary(
                reviewerCredentialId,
                organizationId,
                reviewerId,
                "PILOT_QUALIFICATION",
                "Civil Aviation Authority",
                "LIC-007",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                CredentialStatus.ACTIVE,
                null,
                null,
                null,
                null));
  }

  @Test
  void returnsEmptyWhenCredentialBelongsToDifferentUserInSameOrganization() {
    assertThat(
            credentials.findByIdAndOrganizationIdAndUserId(
                sameOrganizationCredentialId, organizationId, reviewerId))
        .isEmpty();
  }

  @Test
  void returnsEmptyWhenCredentialBelongsToDifferentOrganization() {
    assertThat(
            credentials.findByIdAndOrganizationIdAndUserId(
                otherOrganizationCredentialId, organizationId, otherOrganizationUserId))
        .isEmpty();
  }

  @Test
  void returnsEmptyWhenCredentialIdAndOrganizationDoNotMatch() {
    assertThat(
            credentials.findByIdAndOrganizationIdAndUserId(
                reviewerCredentialId, otherOrganizationId, reviewerId))
        .isEmpty();
  }

  private UUID createUser(String label, UUID ownerOrganizationId) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            ownerOrganizationId);
    user.addRole(UserRole.ORG_ADMIN);
    return users.saveAndFlush(user).getId();
  }

  private UUID insertCredential(UUID userId, UUID ownerOrganizationId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials
            (id, organization_id, user_id, credential_type, issuer, credential_reference,
             issued_at, expires_at, status)
        VALUES (?, ?, ?, 'PILOT_QUALIFICATION', 'Civil Aviation Authority', 'LIC-007',
                '2026-01-01T00:00:00Z', '2027-01-01T00:00:00Z', 'ACTIVE')
        """,
        id,
        ownerOrganizationId,
        userId);
    return id;
  }
}
