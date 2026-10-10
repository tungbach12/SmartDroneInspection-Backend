package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionReadinessDecisionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF2-09 and MF2-10: the assigned Inspector opens a field session on site.
 *
 * <p>The readiness decision is re-checked here rather than trusted from the moment it was made,
 * because MF2-07's approval is only a statement about a source basis at one instant. This is the
 * point at which "stale, expired or revoked readiness" becomes a decision this system can make.
 *
 * <p>No Drone actuation happens here. Start records that the software agreed the paperwork and the
 * pre-flight checklist were in order; it does not arm the aircraft, and the session start time is
 * not hardware flight time.
 *
 * <p>Lock ordering is inspection-first, the same order the preparation and readiness services use,
 * so a start cannot interleave with a preparation submission or a readiness decision on the same
 * inspection.
 */
@Service
public class InspectionFieldSessionService {

  private final InspectionRepository inspections;
  private final InspectionReadinessDecisionRepository decisions;
  private final FieldSessionRepository sessions;
  private final UserAccess userAccess;
  private final EntityManager entityManager;

  public InspectionFieldSessionService(
      InspectionRepository inspections,
      InspectionReadinessDecisionRepository decisions,
      FieldSessionRepository sessions,
      UserAccess userAccess,
      EntityManager entityManager) {
    this.inspections = inspections;
    this.decisions = decisions;
    this.sessions = sessions;
    this.userAccess = userAccess;
    this.entityManager = entityManager;
  }

  /**
   * Opens a field session against the inspection's current approved readiness decision (MF2-10).
   *
   * <p>The pre-flight note is required because MF2-09 has the Inspector identify the assigned Drone
   * and complete the current pre-flight checklist on site before asking to start. Recording that
   * without a note would make the attestation unfalsifiable.
   *
   * <p>{@code checklistTemplateId} is recorded but not resolved here. Checklist templates are
   * platform-scoped rather than organization-scoped — {@code checklist_templates} carries no {@code
   * organization_id} and links only to an asset category — so there is no tenant check to perform
   * and a lookup would add nothing. MF2-11 records checklist responses against the session, which
   * is where the template version becomes auditable.
   */
  @Transactional
  public FieldSession start(
      UUID inspectorId,
      UUID inspectionId,
      UUID checklistTemplateId,
      String preFlightChecklistNote) {
    UUID organizationId = requireInspector(inspectorId);
    if (preFlightChecklistNote == null || preFlightChecklistNote.isBlank()) {
      throw sessionFailure(
          "PREFLIGHT_CHECKLIST_REQUIRED",
          "Record the completed pre-flight checklist before starting the session.");
    }

    Inspection inspection = lockInspection(inspectionId, organizationId);
    if (!inspection.isInspectedBy(inspectorId)) {
      throw sessionFailure("SESSION_SCOPE_DENIED", "This inspection is not assigned to you.");
    }
    // Checked before the inspection status, so a second start is reported as the duplicate it is
    // rather than as "not ready for flight", which would only be true because the first start
    // already moved the inspection to IN_PROGRESS.
    requireNoOpenSession(inspectionId);
    if (inspection.getStatus() != InspectionStatus.READY_FOR_FLIGHT) {
      throw sessionFailure(
          "INSPECTION_NOT_READY_FOR_FLIGHT",
          "The inspection is not ready for flight, so no session may start.");
    }

    InspectionReadinessDecision readiness = currentReadinessDecision(inspectionId);

    FieldSession session =
        FieldSession.opening(
            inspectionId,
            organizationId,
            inspectorId,
            inspection.getDroneId(),
            checklistTemplateId,
            readiness.getId(),
            readiness.getPreparationVersion());
    session.start(Instant.now());

    inspection.moveTo(InspectionStatus.IN_PROGRESS);
    inspections.save(inspection);
    return sessions.saveAndFlush(session);
  }

  /**
   * Records a postponement (MF2-09).
   *
   * <p>The inspection returns to {@code READY_FOR_FLIGHT} because the readiness decision was not
   * consumed: weather and site safety can stop a session that was correctly approved, and the same
   * approval is still the current one.
   */
  @Transactional
  public FieldSession postpone(UUID inspectorId, UUID sessionId, String reason) {
    UUID organizationId = requireInspector(inspectorId);
    FieldSession session = requireOwnedOpenSession(sessionId, organizationId, inspectorId);

    try {
      session.postpone(reason);
    } catch (IllegalArgumentException exception) {
      throw sessionFailure("POSTPONEMENT_REASON_REQUIRED", exception.getMessage());
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SESSION_NOT_IN_PROGRESS", exception.getMessage());
    }
    return reopenInspection(session);
  }

  /** Records an abort (MF2-11). The inspection stays in progress until the session is ended. */
  @Transactional
  public FieldSession abort(UUID inspectorId, UUID sessionId, String reason) {
    UUID organizationId = requireInspector(inspectorId);
    FieldSession session = requireOwnedOpenSession(sessionId, organizationId, inspectorId);

    try {
      session.abort(reason);
    } catch (IllegalArgumentException exception) {
      throw sessionFailure("ABORT_REASON_REQUIRED", exception.getMessage());
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SESSION_NOT_IN_PROGRESS", exception.getMessage());
    }
    return sessions.saveAndFlush(session);
  }

  private FieldSession reopenInspection(FieldSession session) {
    inspections
        .findWithLockByIdAndOrganizationId(session.getInspectionId(), session.getOrganizationId())
        .ifPresent(
            inspection -> {
              if (inspection.getStatus() == InspectionStatus.IN_PROGRESS) {
                inspection.moveTo(InspectionStatus.READY_FOR_FLIGHT);
                inspections.save(inspection);
              }
            });
    return sessions.saveAndFlush(session);
  }

  /**
   * The decision that currently governs the inspection.
   *
   * <p>"Latest" means the most recently {@code decidedAt}. MF2-08 appends an {@code INVALIDATED}
   * decision rather than editing the approval, so an inspection whose newest decision is anything
   * other than {@code APPROVED} refuses to start even though an older approval row still exists.
   */
  private InspectionReadinessDecision currentReadinessDecision(UUID inspectionId) {
    return decisions
        .findFirstByInspectionIdOrderByDecidedAtDesc(inspectionId)
        .filter(decision -> decision.getDecision() == ReadinessDecisionType.APPROVED)
        .orElseThrow(
            () ->
                sessionFailure(
                    "READINESS_NOT_APPROVED",
                    "No current approved readiness decision covers this inspection."));
  }

  private void requireNoOpenSession(UUID inspectionId) {
    boolean open =
        sessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .anyMatch(FieldSession::isOpen);
    if (open) {
      throw sessionFailure(
          "SESSION_ALREADY_IN_PROGRESS", "This inspection already has an open field session.");
    }
  }

  private FieldSession requireOwnedOpenSession(
      UUID sessionId, UUID organizationId, UUID inspectorId) {
    FieldSession session =
        sessions
            .findById(sessionId)
            .filter(candidate -> candidate.getOrganizationId().equals(organizationId))
            .orElseThrow(() -> sessionFailure("SESSION_NOT_FOUND", "Field session was not found."));
    if (!session.getInspectorUserId().equals(inspectorId)) {
      throw sessionFailure("SESSION_SCOPE_DENIED", "This field session is not assigned to you.");
    }
    return session;
  }

  private Inspection lockInspection(UUID inspectionId, UUID organizationId) {
    Inspection inspection =
        inspections
            .findWithLockByIdAndOrganizationId(inspectionId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found."));
    entityManager.refresh(inspection, LockModeType.PESSIMISTIC_WRITE);
    return inspection;
  }

  private UUID requireInspector(UUID inspectorId) {
    UserAccess.ActiveUser user =
        userAccess
            .findActiveUser(inspectorId)
            .filter(candidate -> candidate.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "SESSION_SCOPE_DENIED",
                        "Only an active Inspector may manage a field session."));
    if (user.organizationId() == null) {
      throw sessionFailure("SESSION_SCOPE_DENIED", "This Inspector has no organization scope.");
    }
    return user.organizationId();
  }

  private static BusinessException sessionFailure(String code, String detail) {
    return new BusinessException(HttpStatus.CONFLICT, code, detail);
  }
}
