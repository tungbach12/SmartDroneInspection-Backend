package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.InspectionFieldSessionService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Field-session state transitions, scoping, and validation. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionFieldSessionServiceTest {

  private static final Instant PLANNED_START = Instant.parse("2026-11-01T09:00:00Z");

  @Autowired InspectionRepository inspections;
  @Autowired FieldSessionRepository sessions;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired DroneRepository drones;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserAccess userAccess;
  @Autowired EntityManager entityManager;

  private UUID organizationId;
  private UUID otherOrganizationId;
  private UUID inspectorId;
  private UUID reviewerId;
  private UUID assetId;
  private UUID droneId;
  private UUID inspectionId;
  private long initialSessionCount;
  private InspectionFieldSessionService service;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    otherOrganizationId = fixture.otherOrganizationId();
    reviewerId = createUser(organizationId, UserRole.CUSTOMER, "reviewer");
    inspectorId = createUser(organizationId, UserRole.INSPECTOR, "inspector");

    assetId =
        assets
            .saveAndFlush(
                new Asset(
                    organizationId,
                    fixture.categoryId(),
                    "ASSET-SESSION-" + UUID.randomUUID(),
                    "Session asset",
                    null,
                    "Inspection location",
                    null,
                    null,
                    null,
                    reviewerId))
            .getId();
    droneId =
        drones.saveAndFlush(new Drone(organizationId, "READY-DRONE-" + UUID.randomUUID())).getId();
    inspectionId = createReadyInspection();
    initialSessionCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM field_sessions WHERE inspection_id = ?",
            Long.class,
            inspectionId);

    service = new InspectionFieldSessionService(inspections, sessions, userAccess, entityManager);
  }

  @Test
  void theAssignedInspectorStartsASession() {
    FieldSession session =
        service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");

    assertThat(session.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(session.getInspectionId()).isEqualTo(inspectionId);
    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM field_sessions WHERE inspection_id = ?",
                Long.class,
                inspectionId))
        .isEqualTo(initialSessionCount + 1);
  }

  @Test
  void anInspectionWithoutFlightPermitCannotStart() {
    jdbcTemplate.update(
        "UPDATE inspections SET flight_permit_file_key = NULL WHERE id = ?", inspectionId);
    entityManager.clear();

    assertThatThrownBy(() -> service.start(inspectorId, inspectionId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("FLIGHT_PERMIT_REQUIRED"));
    assertNoSessionStarted();
  }

  @Test
  void anInspectionThatIsNotReadyForFlightCannotStart() {
    jdbcTemplate.update("UPDATE inspections SET status = 'ASSIGNED' WHERE id = ?", inspectionId);
    entityManager.clear();

    assertThatThrownBy(() -> service.start(inspectorId, inspectionId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("INSPECTION_NOT_READY_FOR_FLIGHT"));
    assertNoSessionStarted();
  }

  @Test
  void aSessionCanStartAgainWhenInspectionIsFieldCompleted() {
    FieldSession first = service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");
    service.complete(inspectorId, inspectionId, first.getId(), "First flight completed");
    assertThat(statusOfInspection()).isEqualTo("FIELD_COMPLETED");

    FieldSession second = service.start(inspectorId, inspectionId, "Second flight planned");
    assertThat(second.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void anotherInspectorCannotStartThisSession() {
    UUID otherInspectorId = createUser(organizationId, UserRole.INSPECTOR, "other-inspector");

    assertThatThrownBy(() -> service.start(otherInspectorId, inspectionId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("SESSION_SCOPE_DENIED"));
    assertNoSessionStarted();
  }

  @Test
  void anInspectorFromAnotherOrganizationStartsNothing() {
    UUID outsiderId = createUser(otherOrganizationId, UserRole.INSPECTOR, "outsider");

    assertThatThrownBy(() -> service.start(outsiderId, inspectionId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("INSPECTION_NOT_FOUND"));
    assertNoSessionStarted();
  }

  @Test
  void aBlankPreFlightNoteCannotStart() {
    assertThatThrownBy(() -> service.start(inspectorId, inspectionId, "   "))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("PREFLIGHT_CHECKLIST_REQUIRED"));
    assertNoSessionStarted();
  }

  @Test
  void aSecondStartIsRefusedWhileTheFirstSessionIsStillOpen() {
    service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");

    assertThatThrownBy(
            () -> service.start(inspectorId, inspectionId, "Confirmed bridge is accessible"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("SESSION_ALREADY_IN_PROGRESS"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM field_sessions WHERE inspection_id = ?",
                Long.class,
                inspectionId))
        .isEqualTo(initialSessionCount + 1);
  }

  @Test
  void postponingRecordsTheReasonAndLeavesTheInspectionReadyForAnotherAttempt() {
    FieldSession session =
        service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");

    service.postpone(inspectorId, inspectionId, session.getId(), "High wind above 10 m/s");

    assertThat(sessionStatus(session.getId())).isEqualTo("POSTPONED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT postponement_reason FROM field_sessions WHERE id = ?",
                String.class,
                session.getId()))
        .isEqualTo("High wind above 10 m/s");
    assertThat(statusOfInspection()).isEqualTo("READY_FOR_FLIGHT");
  }

  @Test
  void abortingRecordsTheReasonAndDoesNotReturnTheInspectionToReadyForFlight() {
    FieldSession session =
        service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");

    service.abort(inspectorId, inspectionId, session.getId(), "Structure found unsafe on site");

    assertThat(sessionStatus(session.getId())).isEqualTo("ABORTED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT abort_reason FROM field_sessions WHERE id = ?",
                String.class,
                session.getId()))
        .isEqualTo("Structure found unsafe on site");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT postponement_reason FROM field_sessions WHERE id = ?",
                String.class,
                session.getId()))
        .isNull();
    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void abortingWithoutAReasonIsRefused() {
    FieldSession session =
        service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");

    assertThatThrownBy(() -> service.abort(inspectorId, inspectionId, session.getId(), "  "))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("ABORT_REASON_REQUIRED"));
    assertThat(sessionStatus(session.getId())).isEqualTo("IN_PROGRESS");
  }

  @Test
  void normalCompletionReturnsTheInspectionToMf3WithRecordedOutcome() {
    FieldSession session =
        service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");

    FieldSession completed =
        service.complete(inspectorId, inspectionId, session.getId(), "East span surveyed");

    assertThat(completed.getStatus()).isEqualTo("FIELD_COMPLETED");
    assertThat(completed.getEndedAt()).isNotNull();
    assertThat(completed.getOutcomeNotes()).isEqualTo("East span surveyed");
    assertThat(statusOfInspection()).isEqualTo("FIELD_COMPLETED");
  }

  @Test
  void anAbortedSessionCannotBeCompleted() {
    FieldSession session =
        service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");
    service.abort(inspectorId, inspectionId, session.getId(), "Structure unsafe");

    assertThatThrownBy(
            () ->
                service.complete(inspectorId, inspectionId, session.getId(), "Outcome after abort"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("SESSION_NOT_IN_PROGRESS"));
  }

  @Test
  void anInspectionMayBeStartedAgainAfterAPostponement() {
    FieldSession first = service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");
    service.postpone(inspectorId, inspectionId, first.getId(), "High wind above 10 m/s");

    FieldSession second =
        service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");

    assertThat(second.getId()).isNotEqualTo(first.getId());
    assertThat(sessionStatus(second.getId())).isEqualTo("IN_PROGRESS");
    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void postponingWithoutAReasonIsRefused() {
    FieldSession session =
        service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");

    assertThatThrownBy(() -> service.postpone(inspectorId, inspectionId, session.getId(), "  "))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("POSTPONEMENT_REASON_REQUIRED"));
    assertThat(sessionStatus(session.getId())).isEqualTo("IN_PROGRESS");
  }

  @Test
  void anotherInspectorCannotPostponeThisSession() {
    FieldSession session =
        service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");
    UUID otherInspectorId = createUser(organizationId, UserRole.INSPECTOR, "other-inspector");

    assertThatThrownBy(
            () -> service.postpone(otherInspectorId, inspectionId, session.getId(), "Wind"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("SESSION_SCOPE_DENIED"));
    assertThat(sessionStatus(session.getId())).isEqualTo("IN_PROGRESS");
  }

  @Test
  void listingSessionsReturnsOnlyTheCallersOwnWork() {
    service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");
    UUID otherInspectorId = createUser(organizationId, UserRole.INSPECTOR, "other-inspector");

    assertThat(service.listSessions(inspectorId, inspectionId)).hasSize(1);
    assertThat(service.listSessions(otherInspectorId, inspectionId)).isEmpty();
  }

  @Test
  void listingSessionsOfAnotherOrganizationSeesNothing() {
    service.start(inspectorId, inspectionId, "Confirmed bridge is accessible");
    UUID outsiderId = createUser(otherOrganizationId, UserRole.INSPECTOR, "outsider");

    assertThatThrownBy(() -> service.listSessions(outsiderId, inspectionId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("INSPECTION_NOT_FOUND"));
  }

  @Test
  void closingASessionOfAnotherInspectionIsRefused() {
    UUID sessionId = startSession("Confirmed bridge is accessible").getId();
    UUID unrelatedInspectionId = UUID.randomUUID();

    assertThatThrownBy(
            () -> service.postpone(inspectorId, unrelatedInspectionId, sessionId, "Wind"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error -> assertThat(((BusinessException) error).code()).isEqualTo("SESSION_NOT_FOUND"));
    assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
  }

  private FieldSession startSession(String preFlightNote) {
    return service.start(inspectorId, inspectionId, preFlightNote);
  }

  private void assertNoSessionStarted() {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM field_sessions WHERE inspection_id = ?",
                Long.class,
                inspectionId))
        .isEqualTo(initialSessionCount);
  }

  private UUID createUser(UUID organizationId, UserRole role, String label) {
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

  private UUID createReadyInspection() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, organization_id, asset_id, inspector_id, drone_id, flight_permit_file_key, flight_permit_file_name, flight_permit_uploaded_at, status,
          planned_start_at, planned_end_at)
        VALUES (?, ?, ?, ?, ?, 'permits/test.pdf', 'test.pdf', CURRENT_TIMESTAMP, 'READY_FOR_FLIGHT', ?, ?)
        """,
        id,
        organizationId,
        assetId,
        inspectorId,
        droneId,
        Timestamp.from(PLANNED_START),
        Timestamp.from(PLANNED_START.plus(2, ChronoUnit.HOURS)));
    return id;
  }

  private String sessionStatus(UUID sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM field_sessions WHERE id = ?", String.class, sessionId);
  }

  private String statusOfInspection() {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM inspections WHERE id = ?", String.class, inspectionId);
  }
}
