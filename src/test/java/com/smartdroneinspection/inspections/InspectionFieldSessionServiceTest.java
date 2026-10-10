package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.inspections.repository.InspectionReadinessDecisionRepository;
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

/**
 * MF2-10: starting a field session rechecks readiness at the moment of the start.
 *
 * <p>MF2-07 records that a decision was made against a particular source basis. This test covers
 * the other half of that promise — the decision is only usable while that basis still holds. Every
 * refusal here leaves the inspection and the session table untouched, because a failed recheck must
 * not half-start a mission.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionFieldSessionServiceTest {

  private static final Instant PLANNED_START = Instant.parse("2026-11-01T09:00:00Z");

  @Autowired InspectionRepository inspections;
  @Autowired InspectionPreparationRepository preparations;
  @Autowired InspectionReadinessDecisionRepository decisions;
  @Autowired FieldSessionRepository sessions;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
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
  private UUID readinessDecisionId;
  private UUID checklistTemplateId;
  private InspectionFieldSessionService service;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    otherOrganizationId = fixture.otherOrganizationId();
    checklistTemplateId = fixture.checklistTemplateId();
    reviewerId = createUser(organizationId, UserRole.ORG_ADMIN, "reviewer");
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
    readinessDecisionId = insertApprovedReadinessDecision();

    service =
        new InspectionFieldSessionService(
            inspections, decisions, sessions, userAccess, entityManager);
  }

  @Test
  void theAssignedInspectorStartsASessionAgainstTheCurrentReadinessDecision() {
    FieldSession session =
        service.start(
            inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");

    assertThat(session.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(session.getInspectionId()).isEqualTo(inspectionId);
    assertThat(session.getInspectorUserId()).isEqualTo(inspectorId);
    assertThat(session.getDroneId()).isEqualTo(droneId);
    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM field_sessions", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void theSessionRecordsWhichReadinessDecisionItStartedAgainst() {
    FieldSession session =
        service.start(
            inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");

    assertThat(readinessDecisionIdOf(session.getId())).isEqualTo(readinessDecisionId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT readiness_version FROM field_sessions WHERE id = ?",
                Integer.class,
                session.getId()))
        .isEqualTo(1);
  }

  @Test
  void anInspectionWithoutAnApprovedReadinessDecisionCannotStart() {
    jdbcTemplate.update(
        "DELETE FROM inspection_readiness_decisions WHERE inspection_id = ?", inspectionId);

    assertThatThrownBy(
            () ->
                service.start(inspectorId, inspectionId, checklistTemplateId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("READINESS_NOT_APPROVED"));
    assertNoSessionStarted();
  }

  @Test
  void aReturnedReadinessDecisionCannotStartASession() {
    jdbcTemplate.update(
        "INSERT INTO inspection_readiness_decisions (id, inspection_id, decision,"
            + " reviewed_by_user_id, source_hash, reason) VALUES (?, ?, 'RETURNED', ?, ?, ?)",
        UUID.randomUUID(),
        inspectionId,
        reviewerId,
        "b".repeat(64),
        "Needs rework");
    jdbcTemplate.update("UPDATE inspections SET status = 'PREPARING' WHERE id = ?", inspectionId);

    assertThatThrownBy(
            () ->
                service.start(inspectorId, inspectionId, checklistTemplateId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("INSPECTION_NOT_READY_FOR_FLIGHT"));
    assertNoSessionStarted();
  }

  /**
   * MF2-08: an invalidated decision is the newest one, so a start must refuse even though an older
   * APPROVED row still exists on the same inspection.
   */
  @Test
  void anInvalidatedReadinessDecisionCannotStartASession() {
    jdbcTemplate.update(
        "INSERT INTO inspection_readiness_decisions (id, inspection_id, decision,"
            + " reviewed_by_user_id, source_hash, reason, decided_at)"
            + " VALUES (?, ?, 'INVALIDATED', ?, ?, ?, ?)",
        UUID.randomUUID(),
        inspectionId,
        reviewerId,
        "c".repeat(64),
        "Permit withdrawn after approval",
        Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));

    assertThatThrownBy(
            () ->
                service.start(inspectorId, inspectionId, checklistTemplateId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("READINESS_NOT_APPROVED"));
    assertNoSessionStarted();
  }

  @Test
  void anInspectionThatIsNoLongerReadyForFlightCannotStart() {
    jdbcTemplate.update("UPDATE inspections SET status = 'PREPARING' WHERE id = ?", inspectionId);

    assertThatThrownBy(
            () ->
                service.start(inspectorId, inspectionId, checklistTemplateId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("INSPECTION_NOT_READY_FOR_FLIGHT"));
    assertNoSessionStarted();
  }

  @Test
  void anotherInspectorCannotStartThisSession() {
    UUID otherInspectorId = createUser(organizationId, UserRole.INSPECTOR, "other-inspector");

    assertThatThrownBy(
            () ->
                service.start(
                    otherInspectorId, inspectionId, checklistTemplateId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("SESSION_SCOPE_DENIED"));
    assertNoSessionStarted();
  }

  @Test
  void anInspectorFromAnotherOrganizationStartsNothing() {
    UUID outsiderId = createUser(otherOrganizationId, UserRole.INSPECTOR, "outsider");

    assertThatThrownBy(
            () -> service.start(outsiderId, inspectionId, checklistTemplateId, "Ready at the gate"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("INSPECTION_NOT_FOUND"));
    assertNoSessionStarted();
  }

  @Test
  void aBlankPreFlightNoteCannotStart() {
    assertThatThrownBy(() -> service.start(inspectorId, inspectionId, checklistTemplateId, "   "))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("PREFLIGHT_CHECKLIST_REQUIRED"));
    assertNoSessionStarted();
  }

  @Test
  void aSecondStartIsRefusedWhileTheFirstSessionIsStillOpen() {
    service.start(inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");

    assertThatThrownBy(
            () ->
                service.start(
                    inspectorId,
                    inspectionId,
                    checklistTemplateId,
                    "Confirmed bridge is accessible"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("SESSION_ALREADY_IN_PROGRESS"));
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM field_sessions", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void postponingRecordsTheReasonAndLeavesTheInspectionReadyForAnotherAttempt() {
    FieldSession session =
        service.start(
            inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");

    service.postpone(inspectorId, session.getId(), "High wind above 10 m/s");

    assertThat(sessionStatus(session.getId())).isEqualTo("POSTPONED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT postponement_reason FROM field_sessions WHERE id = ?",
                String.class,
                session.getId()))
        .isEqualTo("High wind above 10 m/s");
    assertThat(statusOfInspection()).isEqualTo("READY_FOR_FLIGHT");
  }

  /**
   * An abort is not a postponement: it does not hand the inspection back for another attempt, and
   * it must not carry a postponement reason.
   */
  @Test
  void abortingRecordsTheReasonAndDoesNotReturnTheInspectionToReadyForFlight() {
    FieldSession session =
        service.start(
            inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");

    service.abort(inspectorId, session.getId(), "Structure found unsafe on site");

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
        service.start(
            inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");

    assertThatThrownBy(() -> service.abort(inspectorId, session.getId(), "  "))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("ABORT_REASON_REQUIRED"));
    assertThat(sessionStatus(session.getId())).isEqualTo("IN_PROGRESS");
  }

  @Test
  void anInspectionMayBeStartedAgainAfterAPostponement() {
    FieldSession first =
        service.start(
            inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");
    service.postpone(inspectorId, first.getId(), "High wind above 10 m/s");

    FieldSession second =
        service.start(
            inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");

    assertThat(second.getId()).isNotEqualTo(first.getId());
    assertThat(sessionStatus(second.getId())).isEqualTo("IN_PROGRESS");
    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void postponingWithoutAReasonIsRefused() {
    FieldSession session =
        service.start(
            inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");

    assertThatThrownBy(() -> service.postpone(inspectorId, session.getId(), "  "))
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
        service.start(
            inspectorId, inspectionId, checklistTemplateId, "Confirmed bridge is accessible");
    UUID otherInspectorId = createUser(organizationId, UserRole.INSPECTOR, "other-inspector");

    assertThatThrownBy(() -> service.postpone(otherInspectorId, session.getId(), "Wind"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("SESSION_SCOPE_DENIED"));
    assertThat(sessionStatus(session.getId())).isEqualTo("IN_PROGRESS");
  }

  private void assertNoSessionStarted() {
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM field_sessions", Long.class))
        .isZero();
    assertThat(statusOfInspection()).isIn("READY_FOR_FLIGHT", "PREPARING");
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
        INSERT INTO inspections (id, organization_id, asset_id, inspector_id, drone_id, status, objective,
          planned_start_at, planned_end_at)
        VALUES (?, ?, ?, ?, ?, 'READY_FOR_FLIGHT', 'Inspect main span', ?, ?)
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

  /**
   * An APPROVED readiness decision whose snapshot carries the source hash the session start
   * rechecks. The real MF2-07 path produces this through {@code ReadinessSnapshotFactory}; the
   * session rule under test is "the decision is usable", not "the hash is computed correctly".
   */
  private UUID insertApprovedReadinessDecision() {
    InspectionPreparation preparation = new InspectionPreparation(inspectionId, inspectorId, 1);
    preparation.recordShotList("{\"shots\":[\"span\"]}");
    preparation.recordSafetyObservations("No hazards observed");
    preparation.submit(inspectorId);
    preparation = preparations.saveAndFlush(preparation);
    preparation.markReady();
    preparations.saveAndFlush(preparation);

    InspectionReadinessDecision decision =
        new InspectionReadinessDecision(
            inspectionId,
            preparation.getId(),
            preparation.getPreparationVersion(),
            ReadinessDecisionType.APPROVED,
            reviewerId,
            null,
            "{\"source_envelope\":{}}",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "a".repeat(64));
    return decisions.saveAndFlush(decision).getId();
  }

  private UUID readinessDecisionIdOf(UUID sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT readiness_decision_id FROM field_sessions WHERE id = ?", UUID.class, sessionId);
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
