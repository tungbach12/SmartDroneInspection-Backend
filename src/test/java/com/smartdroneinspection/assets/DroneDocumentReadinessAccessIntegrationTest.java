package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.readiness.AssetPairReadinessStatus;
import com.smartdroneinspection.assets.readiness.AssetPairReadinessSummary;
import com.smartdroneinspection.assets.readiness.AssignmentReadinessResponse;
import com.smartdroneinspection.assets.readiness.DroneDocumentReadinessAccess;
import com.smartdroneinspection.assets.readiness.DroneDocumentReadinessStatus;
import com.smartdroneinspection.assets.readiness.DroneDocumentSummary;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Organization-scoped read contract for Drone documents and assignment pairs. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DroneDocumentReadinessAccessIntegrationTest {

  @Autowired DroneDocumentReadinessAccess readiness;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired DroneRepository drones;
  @Autowired AssetPairAssignmentRepository pairs;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private UUID organizationId;
  private UUID otherOrganizationId;
  private UUID assignedInspectorId;
  private UUID otherInspectorId;
  private UUID assigningUserId;
  private UUID categoryId;
  private UUID targetAssetId;
  private UUID otherAssetId;
  private UUID targetDroneId;
  private UUID otherDroneId;
  private UUID targetDocumentId;
  private UUID otherDroneDocumentId;
  private UUID otherOrganizationDocumentId;
  private UUID targetPairId;
  private UUID otherOrganizationPairId;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    otherOrganizationId = fixture.otherOrganizationId();
    assignedInspectorId = fixture.managerId();
    otherInspectorId = fixture.otherClientId();
    assigningUserId = fixture.adminId();
    categoryId = fixture.categoryId();

    targetAssetId = createAsset(organizationId, "target-asset", fixture.adminId());
    otherAssetId = createAsset(otherOrganizationId, "other-asset", fixture.otherClientId());
    targetDroneId = createDrone(organizationId, "target-drone");
    otherDroneId = createDrone(organizationId, "other-drone");
    UUID otherOrganizationDroneId = createDrone(otherOrganizationId, "other-org-drone");

    targetDocumentId = insertDocument(targetDroneId, "target-document", fixture.managerId());
    otherDroneDocumentId =
        insertDocument(otherDroneId, "other-drone-document", fixture.managerId());
    otherOrganizationDocumentId =
        insertDocument(otherOrganizationDroneId, "other-org-document", fixture.otherClientId());

    targetPairId =
        insertPair(
            organizationId, targetAssetId, assignedInspectorId, targetDroneId, assigningUserId);
    otherOrganizationPairId =
        insertPair(
            otherOrganizationId,
            otherAssetId,
            otherInspectorId,
            otherOrganizationDroneId,
            assigningUserId);
  }

  @Test
  void returnsOnlyRequestedDocumentsOwnedByTheTargetDroneAndSortsById() {
    UUID unknownDocumentId = UUID.randomUUID();
    List<DroneDocumentSummary> result =
        readiness.findForDrone(
            organizationId,
            targetDroneId,
            List.of(
                otherDroneDocumentId,
                unknownDocumentId,
                targetDocumentId,
                otherOrganizationDocumentId));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().id()).isEqualTo(targetDocumentId);
    assertThat(result.getFirst().droneId()).isEqualTo(targetDroneId);
    assertThat(result.getFirst().documentType()).isEqualTo("AIRWORTHINESS_CERTIFICATE");
    assertThat(result.getFirst().issuer()).isEqualTo("Aviation Authority");
    assertThat(result.getFirst().documentReference()).isEqualTo("CERT-" + targetDocumentId);
    assertThat(result.getFirst().status()).isEqualTo(DroneDocumentReadinessStatus.ACTIVE);
    assertThat(result.getFirst().reviewedByUserId()).isEqualTo(assignedInspectorId);
    assertThat(result.getFirst().reviewedAt()).isEqualTo(Instant.parse("2026-02-01T12:30:00Z"));
  }

  @Test
  void returnsEmptyForUnknownDocumentIds() {
    assertThat(readiness.findForDrone(organizationId, targetDroneId, List.of(UUID.randomUUID())))
        .isEmpty();
  }

  @Test
  void emptyDocumentRequestReturnsEmpty() {
    assertThat(readiness.findForDrone(organizationId, targetDroneId, List.of())).isEmpty();
  }

  @Test
  void resolvesPairOnlyForExactOrganizationPairAndAssetTuple() {
    Instant respondedAt = Instant.parse("2026-01-01T12:00:00Z");
    assertThat(readiness.findPairForInspection(organizationId, targetPairId, targetAssetId))
        .contains(
            new AssetPairReadinessSummary(
                targetPairId,
                organizationId,
                targetAssetId,
                assignedInspectorId,
                targetDroneId,
                AssetPairReadinessStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                AssignmentReadinessResponse.ACCEPTED,
                respondedAt));

    assertThat(
            readiness.findPairForInspection(organizationId, otherOrganizationPairId, otherAssetId))
        .isEmpty();
    assertThat(readiness.findPairForInspection(organizationId, targetPairId, otherAssetId))
        .isEmpty();
  }

  private UUID createAsset(UUID ownerOrganizationId, String label, UUID creatorId) {
    Asset asset =
        new Asset(
            ownerOrganizationId,
            categoryId,
            "ASSET-" + label + "-" + UUID.randomUUID(),
            label,
            null,
            "Test location",
            null,
            null,
            null,
            creatorId);
    return assets.saveAndFlush(asset).getId();
  }

  private UUID createDrone(UUID ownerOrganizationId, String label) {
    return drones
        .saveAndFlush(
            new Drone(
                ownerOrganizationId,
                "DRONE-" + label + "-" + UUID.randomUUID(),
                null,
                null,
                null,
                DroneServiceability.ACTIVE))
        .getId();
  }

  private UUID insertDocument(UUID droneId, String label, UUID uploaderId) {
    UUID documentId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO drone_documents
            (id, drone_id, document_type, issuer, document_reference, valid_from, valid_until,
             status, object_key, checksum_sha256, uploaded_by_user_id, reviewed_by_user_id,
             reviewed_at)
        VALUES (?, ?, 'AIRWORTHINESS_CERTIFICATE', 'Aviation Authority', ?,
                '2026-01-01T00:00:00Z', '2027-01-01T00:00:00Z', 'ACTIVE', ?, ?, ?, ?,
                '2026-02-01T12:30:00Z')
        """,
        documentId,
        droneId,
        "CERT-" + documentId,
        "drone-documents/" + label + "/" + documentId,
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        uploaderId,
        assignedInspectorId);
    return documentId;
  }

  private UUID insertPair(
      UUID ownerOrganizationId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      UUID assignedByUserId) {
    UUID pairId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_pair_assignments
            (id, organization_id, asset_id, inspector_user_id, drone_id, valid_from, valid_until,
             status, assigned_by_user_id, assignment_response, responded_at)
        VALUES (?, ?, ?, ?, ?, '2026-01-01T00:00:00Z', '2027-01-01T00:00:00Z', 'ACTIVE', ?,
                'ACCEPTED', '2026-01-01T12:00:00Z')
        """,
        pairId,
        ownerOrganizationId,
        assetId,
        inspectorId,
        droneId,
        assignedByUserId);
    return pairId;
  }
}
