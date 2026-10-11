package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.AssignmentResponse;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.domain.enums.FlightPermitStatus;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.repository.FlightPermitRepository;
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

/**
 * Verifies the catalog finders resolve against the deployed schema and stay inside one tenant.
 *
 * <p>Spring Data validates derived queries while the context boots, so a mistyped property path
 * fails here rather than at the first request. The tenant assertions matter just as much: every
 * finder used by MF2 takes an organization id precisely so an inspector cannot reach another
 * organization's fleet, permits or pairings by guessing an id.
 *
 * <p>The Testcontainers import is required, not optional: without it the context starts on the
 * docker-compose database, whose Flyway history is whatever the developer last migrated. A shared
 * database then validates this branch's migrations against a stale history and the whole class
 * fails during context startup, which looks like a schema defect rather than a test-isolation one.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CatalogRepositoryQueryTest {

  @Autowired DroneRepository drones;
  @Autowired FlightPermitRepository permits;
  @Autowired AssetPairAssignmentRepository pairings;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  AssetTestFixture.Data data;

  @BeforeEach
  void setUp() {
    data = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
  }

  @Test
  void assignableDroneLookupReturnsOnlyActiveDronesInTheOrganization() {
    Drone active = drones.saveAndFlush(new Drone(data.organizationId(), "DJI-M350-RTK-T01"));
    Drone heldForMaintenance =
        drones.saveAndFlush(new Drone(data.organizationId(), "DJI-M350-RTK-T02"));
    heldForMaintenance.changeServiceability(DroneServiceability.MAINTENANCE);
    drones.saveAndFlush(heldForMaintenance);

    assertThat(drones.findAssignableByOrganizationId(data.organizationId()))
        .containsExactly(active);
    assertThat(drones.findAssignableByOrganizationId(data.otherOrganizationId())).isEmpty();
    assertThat(drones.findByIdAndOrganizationId(active.getId(), data.otherOrganizationId()))
        .isEmpty();
  }

  @Test
  void permitLookupIsScopedToOrganizationAndAsset() {
    assertThat(
            permits.findByOrganizationIdAndAssetIdOrderByCreatedAtDesc(
                data.organizationId(), data.categoryId()))
        .isEmpty();
    assertThat(
            permits.findByOrganizationIdAndAssetIdAndStatusIn(
                data.organizationId(), data.categoryId(), List.of(FlightPermitStatus.ACTIVE)))
        .isEmpty();
    assertThat(
            permits.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                data.otherOrganizationId(), FlightPermitStatus.ACTIVE))
        .isEmpty();
  }

  @Test
  void anActivePairingIsFoundForItsOrganizationOnly() {
    UUID assetId = createAsset(data.organizationId());
    AssetPairAssignment pairing = createActivePairing(data.organizationId(), assetId);

    assertThat(
            pairings
                .findByOrganizationIdAndAssetIdAndStatus(
                    data.organizationId(), assetId, AssetPairAssignmentStatus.ACTIVE)
                .orElseThrow())
        .isEqualTo(pairing);

    assertThat(
            pairings.findByOrganizationIdAndAssetIdAndStatus(
                data.otherOrganizationId(), assetId, AssetPairAssignmentStatus.ACTIVE))
        .isEmpty();
    assertThat(
            pairings.findActiveWithLockByOrganizationIdAndAssetId(
                data.otherOrganizationId(), assetId))
        .isEmpty();
  }

  @Test
  void anAnsweredPairingLeavesTheInspectorInbox() {
    UUID assetId = createAsset(data.organizationId());
    AssetPairAssignment pairing = createActivePairing(data.organizationId(), assetId);

    assertThat(
            pairings.findByOrganizationIdAndInspectorUserIdAndAssignmentResponseIsNull(
                data.organizationId(), pairing.getInspectorUserId()))
        .containsExactly(pairing);

    pairing.respond(pairing.getInspectorUserId(), AssignmentResponse.ACCEPTED, null);
    pairings.saveAndFlush(pairing);

    assertThat(
            pairings.findByOrganizationIdAndInspectorUserIdAndAssignmentResponseIsNull(
                data.organizationId(), pairing.getInspectorUserId()))
        .isEmpty();
    assertThat(
            pairings.findByInspectorUserIdAndAssignmentResponse(
                pairing.getInspectorUserId(), AssignmentResponse.ACCEPTED))
        .containsExactly(pairing);
  }

  private AssetPairAssignment createActivePairing(UUID organizationId, UUID assetId) {
    AssetPairAssignment pairing =
        new AssetPairAssignment(
            organizationId,
            assetId,
            data.clientId(),
            drones.saveAndFlush(new Drone(organizationId, "DJI-" + UUID.randomUUID())).getId(),
            Instant.now().minusSeconds(60),
            data.managerId());
    pairing.activate("Primary crew", null);
    return pairings.saveAndFlush(pairing);
  }

  private UUID createAsset(UUID organizationId) {
    return assets
        .saveAndFlush(
            new Asset(
                organizationId,
                data.categoryId(),
                "ASSET-" + UUID.randomUUID(),
                "Bridge under inspection",
                null,
                null,
                null,
                null,
                null,
                data.managerId()))
        .getId();
  }
}
