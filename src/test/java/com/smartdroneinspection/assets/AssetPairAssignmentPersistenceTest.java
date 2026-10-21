package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AssetPairAssignmentPersistenceTest {

  @Autowired AssetPairAssignmentRepository assetPairAssignments;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired DroneRepository drones;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private UUID organizationId;
  private UUID otherOrganizationId;
  private UUID assignerId;
  private UUID inspectorId;
  private UUID droneId;
  private UUID assetId;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data data =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = data.organizationId();
    otherOrganizationId = data.otherOrganizationId();
    assignerId = data.managerId();

    User inspector =
        new User(
            "inspector-" + UUID.randomUUID() + "@example.test",
            "Inspector One",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    inspector.addRole(UserRole.INSPECTOR);
    inspectorId = users.saveAndFlush(inspector).getId();

    Drone drone = drones.saveAndFlush(new Drone(organizationId, "DJI-M300-" + UUID.randomUUID()));
    droneId = drone.getId();

    Asset asset =
        assets.saveAndFlush(
            new Asset(
                organizationId,
                data.categoryId(),
                "BR-PAIR-" + UUID.randomUUID(),
                "East Span",
                null,
                "{\"label\":\"District 1\"}",
                null,
                null,
                null,
                assignerId));
    assetId = asset.getId();
  }

  @Test
  void persistsAndLoadsAssetPairAssignmentWithOrganizationScope() {
    Instant validFrom = Instant.now().minusSeconds(60);
    AssetPairAssignment pairing =
        new AssetPairAssignment(
            organizationId, assetId, inspectorId, droneId, validFrom, assignerId);
    pairing.activate("Primary operational crew", null);

    AssetPairAssignment saved = assetPairAssignments.saveAndFlush(pairing);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getStatus()).isEqualTo(AssetPairAssignmentStatus.ACTIVE);
    assertThat(saved.getInspectorUserId()).isEqualTo(inspectorId);
    assertThat(saved.getDroneId()).isEqualTo(droneId);

    assertThat(assetPairAssignments.findByIdAndOrganizationId(saved.getId(), organizationId))
        .isPresent();
    assertThat(assetPairAssignments.findByIdAndOrganizationId(saved.getId(), otherOrganizationId))
        .isEmpty();
  }

  @Test
  void allowsMultipleSupersededPairsButOnlyOneActivePairPerAsset() {
    Instant now = Instant.now();
    AssetPairAssignment first =
        new AssetPairAssignment(
            organizationId, assetId, inspectorId, droneId, now.minusSeconds(120), assignerId);
    first.activate("Initial crew", null);
    first.supersede("Shift ended");
    assetPairAssignments.saveAndFlush(first);

    Drone drone2 = drones.saveAndFlush(new Drone(organizationId, "DJI-M350-" + UUID.randomUUID()));
    AssetPairAssignment second =
        new AssetPairAssignment(
            organizationId, assetId, inspectorId, drone2.getId(), now.minusSeconds(60), assignerId);
    second.activate("Second crew", null);
    assetPairAssignments.saveAndFlush(second);

    // Third attempt to create another ACTIVE pair on the same asset must violate
    // uq_asset_pair_assignments_active_asset
    Drone drone3 = drones.saveAndFlush(new Drone(organizationId, "DJI-M30-" + UUID.randomUUID()));
    AssetPairAssignment conflicting =
        new AssetPairAssignment(
            organizationId, assetId, inspectorId, drone3.getId(), now, assignerId);
    conflicting.activate("Conflicting crew", null);

    assertThatThrownBy(() -> assetPairAssignments.saveAndFlush(conflicting))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsInvalidValidityWindowWhereUntilIsBeforeFrom() {
    Instant from = Instant.now();
    Instant until = from.minus(1, ChronoUnit.HOURS);

    AssetPairAssignment pairing =
        new AssetPairAssignment(organizationId, assetId, inspectorId, droneId, from, assignerId);

    assertThatThrownBy(() -> pairing.activate("Invalid window", until))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Pairing validity must end after it starts");
  }
}
