package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.api.dto.request.RespondToAssignmentRequest;
import com.smartdroneinspection.assets.api.dto.response.AssetPairAssignmentResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.AssignmentResponse;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.service.AssetPairAssignmentService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF2-01 and MF2-02 against a real database.
 *
 * <p>The scope rules are the point of this test: an inspector may answer only their own pairing,
 * only within their own organization, and only once.
 */
@SpringBootTest
@Transactional
class AssetPairAssignmentServiceTest {

  @Autowired AssetPairAssignmentService service;
  @Autowired AssetPairAssignmentRepository pairings;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired DroneRepository drones;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  UUID organizationId;
  UUID inspectorId;
  UUID assignerId;
  UUID assetId;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data data =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = data.organizationId();
    assignerId = data.managerId();

    User inspector =
        new User(
            "inspector-" + UUID.randomUUID() + "@example.test",
            "Field Inspector",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    inspector.addRole(UserRole.INSPECTOR);
    inspectorId = users.saveAndFlush(inspector).getId();

    assetId =
        assets
            .saveAndFlush(
                new Asset(
                    organizationId,
                    data.categoryId(),
                    "ASSET-" + UUID.randomUUID(),
                    "Sung Han Bridge",
                    null,
                    null,
                    null,
                    null,
                    null,
                    assignerId))
            .getId();
  }

  @Test
  void anInspectorSeesAndAcceptsTheirOwnUnansweredPairing() {
    AssetPairAssignment pairing = activePairing();

    var inbox = service.listUnansweredAssignments(inspectorId);
    assertThat(inbox).hasSize(1);
    assertThat(inbox.getFirst().assetName()).isEqualTo("Sung Han Bridge");
    assertThat(inbox.getFirst().droneSerialNumber()).isNotBlank();

    AssetPairAssignmentResponse answered =
        service.respond(
            inspectorId,
            pairing.getId(),
            new RespondToAssignmentRequest(AssignmentResponse.ACCEPTED, null));

    assertThat(answered.assignmentResponse()).isEqualTo(AssignmentResponse.ACCEPTED);
    assertThat(answered.respondedAt()).isNotNull();
    assertThat(service.listUnansweredAssignments(inspectorId)).isEmpty();
  }

  @Test
  void decliningSuspendsThePairingAndKeepsTheReason() {
    AssetPairAssignment pairing = activePairing();

    AssetPairAssignmentResponse answered =
        service.respond(
            inspectorId,
            pairing.getId(),
            new RespondToAssignmentRequest(
                AssignmentResponse.REJECTED, "No night-flight qualification"));

    assertThat(answered.assignmentResponse()).isEqualTo(AssignmentResponse.REJECTED);
    assertThat(answered.status()).isEqualTo(AssetPairAssignmentStatus.SUSPENDED);
    assertThat(answered.reason()).isEqualTo("No night-flight qualification");
  }

  @Test
  void decliningWithoutAReasonIsRefused() {
    AssetPairAssignment pairing = activePairing();

    assertThatThrownBy(
            () ->
                service.respond(
                    inspectorId,
                    pairing.getId(),
                    new RespondToAssignmentRequest(AssignmentResponse.REJECTED, "   ")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("ASSIGNMENT_RESPONSE_REJECTED"));
  }

  @Test
  void anotherInspectorCannotAnswerThisPairing() {
    AssetPairAssignment pairing = activePairing();
    User other =
        new User(
            "other-" + UUID.randomUUID() + "@example.test",
            "Other Inspector",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    other.addRole(UserRole.INSPECTOR);
    UUID otherInspectorId = users.saveAndFlush(other).getId();

    assertThatThrownBy(
            () ->
                service.respond(
                    otherInspectorId,
                    pairing.getId(),
                    new RespondToAssignmentRequest(AssignmentResponse.ACCEPTED, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("ASSIGNMENT_SCOPE_DENIED"));
  }

  @Test
  void anInspectorFromAnotherOrganizationSeesNothing() {
    AssetPairAssignment pairing = activePairing();
    UUID otherOrganizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        otherOrganizationId,
        "Other org",
        "Other org",
        "ORG-" + otherOrganizationId);

    User outsider =
        new User(
            "outsider-" + UUID.randomUUID() + "@example.test",
            "Outsider",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            otherOrganizationId);
    outsider.addRole(UserRole.INSPECTOR);
    UUID outsiderId = users.saveAndFlush(outsider).getId();

    assertThat(service.listUnansweredAssignments(outsiderId)).isEmpty();
    assertThatThrownBy(
            () ->
                service.respond(
                    outsiderId,
                    pairing.getId(),
                    new RespondToAssignmentRequest(AssignmentResponse.ACCEPTED, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("ASSIGNMENT_NOT_FOUND"));
  }

  @Test
  void anInactiveOrAlreadyAnsweredPairingIsRefused() {
    AssetPairAssignment pairing = activePairing();
    service.respond(
        inspectorId,
        pairing.getId(),
        new RespondToAssignmentRequest(AssignmentResponse.ACCEPTED, null));

    assertThatThrownBy(
            () ->
                service.respond(
                    inspectorId,
                    pairing.getId(),
                    new RespondToAssignmentRequest(AssignmentResponse.ACCEPTED, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("ASSIGNMENT_RESPONSE_REJECTED"));
  }

  @Test
  void aSuspendedPairingIsNotOfferedForAResponse() {
    AssetPairAssignment pairing = activePairing();
    pairing.suspend("Crew unavailable");
    pairings.saveAndFlush(pairing);

    assertThat(service.listUnansweredAssignments(inspectorId)).isEmpty();
    assertThatThrownBy(
            () ->
                service.respond(
                    inspectorId,
                    pairing.getId(),
                    new RespondToAssignmentRequest(AssignmentResponse.ACCEPTED, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("ASSIGNMENT_NOT_ACTIVE"));
  }

  private AssetPairAssignment activePairing() {
    Drone drone = drones.saveAndFlush(new Drone(organizationId, "DJI-M350-" + UUID.randomUUID()));
    drone.changeServiceability(DroneServiceability.ACTIVE);
    drones.saveAndFlush(drone);

    AssetPairAssignment pairing =
        new AssetPairAssignment(
            organizationId,
            assetId,
            inspectorId,
            drone.getId(),
            Instant.now().minusSeconds(60),
            assignerId);
    pairing.activate("Primary crew", null);
    return pairings.saveAndFlush(pairing);
  }
}
