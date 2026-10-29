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
  private static final String SESSION_SCOPE_DENIED = "SESSION_SCOPE_DENIED";
  private static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
  private static final String SESSION_NOT_IN_PROGRESS = "SESSION_NOT_IN_PROGRESS";
  private static final String FIELD_SESSION_NOT_FOUND = "Field session was not found.";

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
    UUID organizationId = requireInspector(inspectorId);
    if (preFlightChecklistNote == null || preFlightChecklistNote.isBlank()) {
      throw sessionFailure(
          "PREFLIGHT_CHECKLIST_REQUIRED",
          "Record the completed pre-flight checklist before starting the session.");
    }

    Inspection inspection = lockInspection(inspectionId, organizationId, inspectorId);
    if (!inspection.isInspectedBy(inspectorId)) {
      throw sessionFailure(SESSION_SCOPE_DENIED, "This inspection is not assigned to you.");
    }
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
    UUID organizationId = requireInspector(inspectorId);
    Inspection inspection =
        inspections
            .findAssignedInspectorInspection(inspectionId, organizationId, inspectorId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found."));
    if (!inspection.isInspectedBy(inspectorId)) {
      return List.of();
    }
    return sessions.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
  }

  @Transactional
  public FieldSession postpone(UUID inspectorId, UUID inspectionId, UUID sessionId, String reason) {
    UUID organizationId = requireInspector(inspectorId);
    FieldSession session =
        requireOwnedOpenSession(inspectionId, sessionId, organizationId, inspectorId);

    try {
      session.postpone(reason);
    } catch (IllegalArgumentException exception) {
      throw sessionFailure("POSTPONEMENT_REASON_REQUIRED", exception.getMessage());
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, SESSION_NOT_IN_PROGRESS, exception.getMessage());
    }
    return reopenInspection(session, organizationId, inspectorId);
  }

  @Transactional
  public FieldSession abort(UUID inspectorId, UUID inspectionId, UUID sessionId, String reason) {
    UUID organizationId = requireInspector(inspectorId);
    FieldSession session =
        requireOwnedOpenSession(inspectionId, sessionId, organizationId, inspectorId);

    try {
      session.abort(reason);
    } catch (IllegalArgumentException exception) {
      throw sessionFailure("ABORT_REASON_REQUIRED", exception.getMessage());
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, SESSION_NOT_IN_PROGRESS, exception.getMessage());
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
    UUID organizationId = requireInspector(inspectorId);
    Inspection inspection = lockInspection(inspectionId, organizationId, inspectorId);
    if (!inspection.isInspectedBy(inspectorId)) {
      throw sessionFailure(SESSION_SCOPE_DENIED, "This inspection is not assigned to you.");
    }
    FieldSession session =
        sessions
            .findByIdAndInspectionId(sessionId, inspectionId)
            .orElseThrow(() -> sessionFailure(SESSION_NOT_FOUND, FIELD_SESSION_NOT_FOUND));
    if (!session.isOpen() || !session.isInProgress()) {
      throw sessionFailure(SESSION_NOT_IN_PROGRESS, "This session is not in progress.");
    }
    if (inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
      throw sessionFailure(SESSION_NOT_IN_PROGRESS, "The inspection has no active field session.");
    }
    try {
      session.complete(outcomeNotes);
      inspection.completeFieldSession();
    } catch (IllegalArgumentException exception) {
      throw sessionFailure("SESSION_OUTCOME_REQUIRED", exception.getMessage());
    } catch (IllegalStateException exception) {
      throw sessionFailure(SESSION_NOT_IN_PROGRESS, exception.getMessage());
    }
    inspections.saveAndFlush(inspection);
    return sessions.saveAndFlush(session);
  }

  private FieldSession reopenInspection(
      FieldSession session, UUID organizationId, UUID inspectorId) {
    inspections
        .findAssignedInspectorInspectionWithLock(
            session.getInspectionId(), organizationId, inspectorId)
        .ifPresent(
            inspection -> {
              if (inspection.getStatus() == InspectionStatus.IN_PROGRESS) {
                inspection.moveTo(InspectionStatus.READY_FOR_FLIGHT);
                inspections.save(inspection);
              }
            });
    return sessions.saveAndFlush(session);
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
      UUID inspectionId, UUID sessionId, UUID organizationId, UUID inspectorId) {
    Inspection inspection =
        inspections
            .findAssignedInspectorInspection(inspectionId, organizationId, inspectorId)
            .orElseThrow(() -> sessionFailure(SESSION_NOT_FOUND, FIELD_SESSION_NOT_FOUND));
    FieldSession session =
        sessions
            .findByIdAndInspectionId(sessionId, inspectionId)
            .orElseThrow(() -> sessionFailure(SESSION_NOT_FOUND, FIELD_SESSION_NOT_FOUND));
    if (!inspection.isInspectedBy(inspectorId)) {
      throw sessionFailure(SESSION_SCOPE_DENIED, "This field session is not assigned to you.");
    }
    return session;
  }

  private Inspection lockInspection(UUID inspectionId, UUID organizationId, UUID inspectorId) {
    Inspection inspection =
        inspections
            .findAssignedInspectorInspectionWithLock(inspectionId, organizationId, inspectorId)
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
                        SESSION_SCOPE_DENIED,
                        "Only an active Inspector may manage a field session."));
    if (user.organizationId() == null) {
      throw sessionFailure(SESSION_SCOPE_DENIED, "This Inspector has no organization scope.");
    }
    return user.organizationId();
  }

  private static BusinessException sessionFailure(String code, String detail) {
    return new BusinessException(HttpStatus.CONFLICT, code, detail);
  }
}
