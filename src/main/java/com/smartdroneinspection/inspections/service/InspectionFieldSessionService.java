package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionFieldSessionService {

  private final InspectionRepository inspections;
  private final FieldSessionRepository sessions;
  private final UserAccess userAccess;
  private final EntityManager entityManager;

  public InspectionFieldSessionService(
      InspectionRepository inspections,
      FieldSessionRepository sessions,
      UserAccess userAccess,
      EntityManager entityManager) {
    this.inspections = inspections;
    this.sessions = sessions;
    this.userAccess = userAccess;
    this.entityManager = entityManager;
  }

  @Transactional
  public FieldSession start(UUID inspectorId, UUID inspectionId, String preFlightChecklistNote) {
    UserAccess.ActiveUser inspector = requireInspector(inspectorId);
    if (preFlightChecklistNote == null || preFlightChecklistNote.isBlank()) {
      throw sessionFailure(
          "PREFLIGHT_CHECKLIST_REQUIRED",
          "Record the completed pre-flight checklist before starting the session.");
    }
    Inspection inspection = lockAssignedInspection(inspectionId, inspector);
    requireAssignedInspector(inspection, inspectorId);
    requireNoOpenSession(inspectionId);
    if (!inspection.hasFlightPermit()) {
      throw sessionFailure(
          "FLIGHT_PERMIT_REQUIRED",
          "A flight permit must be uploaded before starting the field session.");
    }
    if (inspection.getStatus() != InspectionStatus.READY_FOR_FLIGHT
        && inspection.getStatus() != InspectionStatus.FIELD_COMPLETED) {
      throw sessionFailure(
          "INSPECTION_NOT_READY_FOR_FLIGHT",
          "The inspection is not ready for flight, so no session may start.");
    }
    FieldSession session = FieldSession.opening(inspectionId);
    session.recordPreFlightChecklistNote(preFlightChecklistNote);
    session.start(Instant.now());
    inspection.moveTo(InspectionStatus.IN_PROGRESS);
    inspections.save(inspection);
    return sessions.saveAndFlush(session);
  }

  @Transactional(readOnly = true)
  public List<FieldSession> listSessions(UUID inspectorId, UUID inspectionId) {
    UserAccess.ActiveUser inspector = requireInspector(inspectorId);
    Inspection inspection = findAssignedInspection(inspectionId, inspector);
    if (inspection == null || !inspection.isInspectedBy(inspectorId)) return List.of();
    return sessions.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
  }

  @Transactional
  public FieldSession postpone(UUID inspectorId, UUID inspectionId, UUID sessionId, String reason) {
    UserAccess.ActiveUser inspector = requireInspector(inspectorId);
    Inspection inspection = findAssignedInspection(inspectionId, inspector);
    if (inspection == null)
      throw sessionFailure("SESSION_NOT_FOUND", "Field session was not found.");
    FieldSession session =
        requireOwnedOpenSession(inspectionId, sessionId, inspection, inspectorId);
    try {
      session.postpone(reason);
    } catch (IllegalArgumentException exception) {
      throw sessionFailure("POSTPONEMENT_REASON_REQUIRED", exception.getMessage());
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "SESSION_NOT_IN_PROGRESS", exception.getMessage());
    }
    return reopenInspection(session, inspection);
  }

  @Transactional
  public FieldSession abort(UUID inspectorId, UUID inspectionId, UUID sessionId, String reason) {
    UserAccess.ActiveUser inspector = requireInspector(inspectorId);
    Inspection inspection = findAssignedInspection(inspectionId, inspector);
    if (inspection == null)
      throw sessionFailure("SESSION_NOT_FOUND", "Field session was not found.");
    FieldSession session =
        requireOwnedOpenSession(inspectionId, sessionId, inspection, inspectorId);
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

  @Transactional
  public FieldSession complete(
      UUID inspectorId, UUID inspectionId, UUID sessionId, String outcomeNotes) {
    if (outcomeNotes == null || outcomeNotes.isBlank()) {
      throw sessionFailure(
          "SESSION_OUTCOME_REQUIRED", "Record the completed field-session outcome.");
    }
    UserAccess.ActiveUser inspector = requireInspector(inspectorId);
    Inspection inspection = lockAssignedInspection(inspectionId, inspector);
    requireAssignedInspector(inspection, inspectorId);
    FieldSession session =
        sessions
            .findByIdAndInspectionId(sessionId, inspectionId)
            .orElseThrow(() -> sessionFailure("SESSION_NOT_FOUND", "Field session was not found."));
    if (!session.isOpen()
        || !session.isInProgress()
        || inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
      throw sessionFailure("SESSION_NOT_IN_PROGRESS", "This session is not in progress.");
    }
    try {
      session.complete(outcomeNotes);
      inspection.completeFieldSession();
    } catch (IllegalArgumentException exception) {
      throw sessionFailure("SESSION_OUTCOME_REQUIRED", exception.getMessage());
    } catch (IllegalStateException exception) {
      throw sessionFailure("SESSION_NOT_IN_PROGRESS", exception.getMessage());
    }
    inspections.saveAndFlush(inspection);
    return sessions.saveAndFlush(session);
  }

  private FieldSession reopenInspection(FieldSession session, Inspection inspection) {
    Inspection locked = lockInspection(inspection);
    if (locked.getStatus() == InspectionStatus.IN_PROGRESS) {
      locked.moveTo(InspectionStatus.READY_FOR_FLIGHT);
      inspections.save(locked);
    }
    return sessions.saveAndFlush(session);
  }

  private FieldSession requireOwnedOpenSession(
      UUID inspectionId, UUID sessionId, Inspection inspection, UUID inspectorId) {
    requireAssignedInspector(inspection, inspectorId);
    return sessions
        .findByIdAndInspectionId(sessionId, inspectionId)
        .filter(FieldSession::isOpen)
        .orElseThrow(() -> sessionFailure("SESSION_NOT_FOUND", "Field session was not found."));
  }

  private void requireAssignedInspector(Inspection inspection, UUID inspectorId) {
    if (!inspection.isInspectedBy(inspectorId)) {
      throw sessionFailure("SESSION_SCOPE_DENIED", "This inspection is not assigned to you.");
    }
  }

  private Inspection lockAssignedInspection(UUID inspectionId, UserAccess.ActiveUser actor) {
    Inspection inspection = findAssignedInspection(inspectionId, actor);
    if (inspection == null) throw sessionFailure("SESSION_NOT_FOUND", "Inspection was not found.");
    return lockInspection(inspection);
  }

  private Inspection findAssignedInspection(UUID inspectionId, UserAccess.ActiveUser actor) {
    if ("INSPECTOR_PARTNER".equals(actor.actorZone())) {
      return inspections
          .findByIdAndInspectorIdAndInspectorPartnerInspectionTrue(inspectionId, actor.id())
          .orElse(null);
    }
    if (actor.organizationId() == null) return null;
    return inspections.findByIdAndOrganizationId(inspectionId, actor.organizationId()).orElse(null);
  }

  private Inspection lockInspection(Inspection inspection) {
    Inspection locked =
        inspections
            .findWithLockByIdAndOrganizationId(inspection.getId(), inspection.getOrganizationId())
            .orElseThrow(() -> sessionFailure("SESSION_NOT_FOUND", "Inspection was not found."));
    entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
    return locked;
  }

  private void requireNoOpenSession(UUID inspectionId) {
    if (sessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .anyMatch(FieldSession::isOpen)) {
      throw sessionFailure(
          "SESSION_ALREADY_IN_PROGRESS", "This inspection already has an open field session.");
    }
  }

  private UserAccess.ActiveUser requireInspector(UUID inspectorId) {
    return userAccess
        .findActiveUser(inspectorId)
        .filter(candidate -> candidate.hasRole(Roles.INSPECTOR))
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "SESSION_SCOPE_DENIED",
                    "Only an active Inspector may manage a field session."));
  }

  private static BusinessException sessionFailure(String code, String detail) {
    return new BusinessException(HttpStatus.CONFLICT, code, detail);
  }
}
