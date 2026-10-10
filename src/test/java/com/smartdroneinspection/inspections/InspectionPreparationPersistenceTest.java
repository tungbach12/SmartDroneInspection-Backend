package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
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

/**
 * {@link InspectionPreparation} against the real V25 table.
 *
 * <p>The unit test proves the transitions. This one proves the mapping: the JSONB shot-list, the
 * optimistic-lock column, and the {@code (inspection_id, preparation_version)} uniqueness the whole
 * revision history depends on. A mapping typo would only surface here, and it would surface inside
 * a later feature's service rather than where it was introduced.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionPreparationPersistenceTest {

  @PersistenceContext EntityManager entityManager;

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;

  UUID organizationId;
  UUID inspectionId;
  UUID inspectorId;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data data =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = data.organizationId();
    inspectorId = createInspector(organizationId);

    UUID assetId = createAsset(organizationId, data.categoryId(), inspectorId);
    inspectionId = createInspection(organizationId, assetId, inspectorId);
  }

  @Test
  void aDraftRoundTripsWithItsJsonbShotListAndTimestamps() {
    InspectionPreparation saved = persist(draftWithShotList(1));

    entityManager.clear();

    InspectionPreparation reloaded = entityManager.find(InspectionPreparation.class, saved.getId());
    assertThat(reloaded.getInspectionId()).isEqualTo(inspectionId);
    assertThat(reloaded.getInspectorUserId()).isEqualTo(inspectorId);
    assertThat(reloaded.getPreparationVersion()).isEqualTo(1);
    assertThat(reloaded.getStatus()).isEqualTo(InspectionPreparationStatus.DRAFT);
    assertThat(reloaded.getShotList()).contains("Span P4");
    assertThat(reloaded.getSubmittedAt()).isNull();
    assertThat(reloaded.getCreatedAt()).isNotNull();
  }

  @Test
  void aSubmissionIsPersistedWithAnAttributableTimestamp() {
    InspectionPreparation submitted = draftWithShotList(1);
    submitted.recordSafetyObservations("Live 110V near pier 3");
    submitted.submit(inspectorId);
    persist(submitted);

    entityManager.clear();

    InspectionPreparation reloaded =
        entityManager.find(InspectionPreparation.class, submitted.getId());
    assertThat(reloaded.getStatus()).isEqualTo(InspectionPreparationStatus.SUBMITTED);
    assertThat(reloaded.getSubmittedAt()).isNotNull();
  }

  @Test
  void twoPreparationsCannotShareAVersionForOneInspection() {
    persist(draftWithShotList(1));

    assertThatThrownBy(() -> persist(draftWithShotList(1)))
        .hasMessageContaining("uq_inspection_preparations_version");
  }

  @Test
  void separateInspectionsMayBothUseVersionOne() {
    persist(draftWithShotList(1));

    UUID otherInspectionId = createSecondInspection();
    InspectionPreparation other = new InspectionPreparation(otherInspectionId, inspectorId, 1);
    other.recordShotList(
        """
        [{"component":"Pier base","modality":"RGB","required":true}]\
        """);

    persist(other);

    assertThat(other.getPreparationVersion()).isEqualTo(1);
    assertThat(other.getInspectionId()).isNotEqualTo(inspectionId);
  }

  private UUID createSecondInspection() {
    UUID otherOrganizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, 'Other', 'Other', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        otherOrganizationId,
        "ORG-" + otherOrganizationId);

    UUID assetId = createAsset(otherOrganizationId, categoryId(otherOrganizationId), inspectorId);
    return createInspection(otherOrganizationId, assetId, inspectorId);
  }

  private UUID createAsset(UUID ownerOrganizationId, UUID categoryId, UUID creatorId) {
    return assets
        .saveAndFlush(
            new Asset(
                ownerOrganizationId,
                categoryId,
                "ASSET-" + UUID.randomUUID(),
                "Sung Han Bridge",
                null,
                null,
                null,
                null,
                null,
                creatorId))
        .getId();
  }

  private UUID createInspection(UUID ownerOrganizationId, UUID assetId, UUID creatorId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, organization_id, asset_id, status, objective)
        VALUES (?, ?, ?, 'ASSIGNED', 'Routine span inspection')
        """,
        id,
        ownerOrganizationId,
        assetId);
    return id;
  }

  private UUID categoryId(UUID ownerOrganizationId) {
    UUID id = UUID.randomUUID();
    String code = "BRIDGE-" + id;
    jdbcTemplate.update(
        "INSERT INTO asset_categories (id, organization_id, code, name) VALUES (?, ?, ?, 'Bridge')",
        id,
        ownerOrganizationId,
        code.toUpperCase());
    return id;
  }

  private InspectionPreparation draftWithShotList(int version) {
    InspectionPreparation preparation =
        new InspectionPreparation(inspectionId, inspectorId, version);
    preparation.recordShotList(
        """
        [{"component":"Span P4","modality":"RGB","required":true}]\
        """);
    return preparation;
  }

  private InspectionPreparation persist(InspectionPreparation preparation) {
    entityManager.persist(preparation);
    entityManager.flush();
    return preparation;
  }

  private UUID createInspector(UUID organizationId) {
    User inspector =
        new User(
            "inspector-" + UUID.randomUUID() + "@example.test",
            "Field Inspector",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    inspector.addRole(UserRole.INSPECTOR);
    return users.saveAndFlush(inspector).getId();
  }
}
