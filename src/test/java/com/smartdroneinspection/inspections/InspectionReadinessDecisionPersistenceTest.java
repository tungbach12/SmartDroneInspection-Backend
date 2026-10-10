package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Maps the readiness decision snapshots to the real V25 PostgreSQL table. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionReadinessDecisionPersistenceTest {

  @PersistenceContext EntityManager entityManager;

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired ObjectMapper objectMapper;

  UUID inspectionId;
  UUID reviewerId;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data data =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    reviewerId = createReviewer(data.organizationId());
    UUID assetId =
        assets
            .saveAndFlush(
                new Asset(
                    data.organizationId(),
                    data.categoryId(),
                    "ASSET-" + UUID.randomUUID(),
                    "Sung Han Bridge",
                    null,
                    null,
                    null,
                    null,
                    null,
                    reviewerId))
            .getId();
    inspectionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, organization_id, asset_id, status, objective)
        VALUES (?, ?, ?, 'ASSIGNED', 'Readiness decision persistence test')
        """,
        inspectionId,
        data.organizationId(),
        assetId);
  }

  @Test
  void decisionSnapshotsAndSourceHashRoundTrip() {
    InspectionPreparation preparation = new InspectionPreparation(inspectionId, reviewerId, 2);
    entityManager.persist(preparation);
    entityManager.flush();
    UUID preparationId = preparation.getId();
    String permitSnapshot = "[{\"id\":\"permit-1\",\"status\":\"ACTIVE\"}]";
    String permitIds = "[\"permit-1\"]";
    InspectionReadinessDecision decision =
        new InspectionReadinessDecision(
            inspectionId,
            preparationId,
            2,
            ReadinessDecisionType.APPROVED,
            reviewerId,
            null,
            permitSnapshot,
            "[]",
            "[]",
            permitIds,
            "[]",
            "[]",
            "sha256:9e107d9d372bb6826bd81d3542a419d6");

    entityManager.persist(decision);
    entityManager.flush();
    UUID decisionId = decision.getId();
    entityManager.clear();

    InspectionReadinessDecision reloaded =
        entityManager.find(InspectionReadinessDecision.class, decisionId);
    assertThat(reloaded.getInspectionId()).isEqualTo(inspectionId);
    assertThat(reloaded.getPreparationId()).isEqualTo(preparationId);
    assertThat(reloaded.getPreparationVersion()).isEqualTo(2);
    assertThat(reloaded.getDecision()).isEqualTo(ReadinessDecisionType.APPROVED);
    assertThat(reloaded.getReviewedByUserId()).isEqualTo(reviewerId);
    assertThat(reloaded.getDecidedAt()).isNotNull();
    assertThat(readJson(reloaded.getPermitSnapshot())).isEqualTo(readJson(permitSnapshot));
    assertThat(readJson(reloaded.getPermitSnapshotIds())).isEqualTo(readJson(permitIds));
    assertThat(reloaded.getSourceHash()).isEqualTo("sha256:9e107d9d372bb6826bd81d3542a419d6");
  }

  @Test
  void aReturnedDecisionWithReasonPersists() {
    InspectionReadinessDecision decision =
        new InspectionReadinessDecision(
            inspectionId,
            null,
            null,
            ReadinessDecisionType.RETURNED,
            reviewerId,
            "Permit validity does not cover planned time",
            null,
            null,
            null,
            null,
            null,
            null,
            "sha256:returned");

    entityManager.persist(decision);
    entityManager.flush();
    entityManager.clear();

    InspectionReadinessDecision reloaded =
        entityManager.find(InspectionReadinessDecision.class, decision.getId());
    assertThat(reloaded.getDecision()).isEqualTo(ReadinessDecisionType.RETURNED);
    assertThat(reloaded.getReason()).contains("validity");
    assertThat(reloaded.getPreparationId()).isNull();
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JacksonException exception) {
      throw new AssertionError("Persisted JSONB value was not valid JSON", exception);
    }
  }

  private UUID createReviewer(UUID organizationId) {
    User reviewer =
        new User(
            "reviewer-" + UUID.randomUUID() + "@example.test",
            "Qualified Reviewer",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    reviewer.addRole(UserRole.ORG_ADMIN);
    return users.saveAndFlush(reviewer).getId();
  }
}
