package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
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
  private final InspectionPreparationRepository preparations;
  private final FieldSessionRepository sessions;
  private final UserAccess userAccess;
  private final EntityManager entityManager;

  public InspectionFieldSessionService(
      InspectionRepository inspections,
      InspectionPreparationRepository preparations,
      FieldSessionRepository sessions,
      UserAccess userAccess,
      EntityManager entityManager) {
    this.inspections = inspections;
    this.preparations = preparations;
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

    Inspection inspection = lockInspection(inspectionId, organizationId);
    if (!inspection.isInspectedBy(inspectorId)) {
      throw sessionFailure("SESSION_SCOPE_DENIED", "This inspection is not assigned to you.");
    }
    requireNoOpenSession(inspectionId);
    if (inspection.getStatus() != InspectionStatus.READY_FOR_FLIGHT) {
      throw sessionFailure(
          "INSPECTION_NOT_READY_FOR_FLIGHT",
          "The inspection is not ready for flight, so no session may start.");
    }

    InspectionPreparation currentPreparation = currentReadyPreparation(inspectionId);

    FieldSession session = FieldSession.opening(inspectionId, currentPreparation.getId());
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
            .findByIdAndOrganizationId(inspectionId, organizationId)
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
          HttpStatus.CONFLICT, "SESSION_NOT_IN_PROGRESS", exception.getMessage());
    }
    return reopenInspection(session, organizationId);
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
    UUID organizationId = requireInspector(inspectorId);
    Inspection inspection = lockInspection(inspectionId, organizationId);
    if (!inspection.isInspectedBy(inspectorId)) {
      throw sessionFailure("SESSION_SCOPE_DENIED", "This inspection is not assigned to you.");
    }
    FieldSession session =
        sessions
            .findByIdAndInspectionId(sessionId, inspectionId)
            .orElseThrow(() -> sessionFailure("SESSION_NOT_FOUND", "Field session was not found."));
    if (!session.isOpen() || !session.isInProgress()) {
      throw sessionFailure("SESSION_NOT_IN_PROGRESS", "This session is not in progress.");
    }
    InspectionPreparation currentPreparation =
        preparations
            .findFirstByInspectionIdOrderByPreparationVersionDesc(inspectionId)
            .orElse(null);
    if (session.getPreparationId() == null
        || currentPreparation == null
        || currentPreparation.getStatus() != InspectionPreparationStatus.READY
        || currentPreparation.getReviewedByUserId() == null
        || !session.getPreparationId().equals(currentPreparation.getId())) {
      throw sessionFailure(
          "SESSION_READINESS_STALE",
          "This session was opened against a readiness decision that is no longer current.");
    }
    if (inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
      throw sessionFailure(
          "SESSION_NOT_IN_PROGRESS", "The inspection has no active field session.");
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

  private FieldSession reopenInspection(FieldSession session, UUID organizationId) {
    inspections
        .findWithLockByIdAndOrganizationId(session.getInspectionId(), organizationId)
        .ifPresent(
            inspection -> {
              if (inspection.getStatus() == InspectionStatus.IN_PROGRESS) {
                inspection.moveTo(InspectionStatus.READY_FOR_FLIGHT);
                inspections.save(inspection);
              }
            });
    return sessions.saveAndFlush(session);
  }

  private InspectionPreparation currentReadyPreparation(UUID inspectionId) {
    return preparations
        .findFirstByInspectionIdOrderByPreparationVersionDesc(inspectionId)
        .filter(preparation -> preparation.getStatus() == InspectionPreparationStatus.READY)
        .filter(preparation -> preparation.getReviewedByUserId() != null)
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
      UUID inspectionId, UUID sessionId, UUID organizationId, UUID inspectorId) {
    Inspection inspection =
        inspections
            .findByIdAndOrganizationId(inspectionId, organizationId)
            .orElseThrow(() -> sessionFailure("SESSION_NOT_FOUND", "Field session was not found."));
    FieldSession session =
        sessions
            .findByIdAndInspectionId(sessionId, inspectionId)
            .orElseThrow(() -> sessionFailure("SESSION_NOT_FOUND", "Field session was not found."));
    if (!inspection.isInspectedBy(inspectorId)) {
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
