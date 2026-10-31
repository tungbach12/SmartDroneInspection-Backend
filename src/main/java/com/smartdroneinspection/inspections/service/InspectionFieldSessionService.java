package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.events.FieldSessionAbortedEvent;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionFieldSessionService {

  private final InspectionRepository inspections;
  private final FieldSessionRepository sessions;
  private final UserAccess userAccess;
  private final EntityManager entityManager;
  private final ApplicationEventPublisher eventPublisher;
  private static final String SESSION_SCOPE_DENIED = "SESSION_SCOPE_DENIED";
  private static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
  private static final String SESSION_NOT_IN_PROGRESS = "SESSION_NOT_IN_PROGRESS";
  private static final String FIELD_SESSION_NOT_FOUND = "Field session was not found.";

  public InspectionFieldSessionService(
      InspectionRepository inspections,
      FieldSessionRepository sessions,
      UserAccess userAccess,
      EntityManager entityManager) {
    this(inspections, sessions, userAccess, entityManager, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public InspectionFieldSessionService(
      InspectionRepository inspections,
      FieldSessionRepository sessions,
      UserAccess userAccess,
      EntityManager entityManager,
      ApplicationEventPublisher eventPublisher) {
    this.inspections = inspections;
    this.sessions = sessions;
    this.userAccess = userAccess;
    this.entityManager = entityManager;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public FieldSession start(UUID inspectorId, UUID inspectionId, String preFlightChecklistNote) {
    boolean partnerAssignment = isInspectorPartner(inspectorId);
    UUID organizationId = requireInspectorOrganization(inspectorId, partnerAssignment);
    if (preFlightChecklistNote == null || preFlightChecklistNote.isBlank()) {
      throw sessionFailure(
          "PREFLIGHT_CHECKLIST_REQUIRED",
          "Record the completed pre-flight checklist before starting the session.");
    }

    Inspection inspection =
        lockInspection(inspectionId, organizationId, inspectorId, partnerAssignment);
    if (!inspection.isInspectedBy(inspectorId)) {
      throw sessionFailure(SESSION_SCOPE_DENIED, "This inspection is not assigned to you.");
    }
    requireNoOpenSession(inspectionId);
    if (!inspection.hasFlightPermit()) {
      throw sessionFailure(
          "FLIGHT_PERMIT_REQUIRED",
          "A flight permit must be uploaded before starting the field session.");
    }
    if (inspection.getStatus() != InspectionStatus.SCHEDULED
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
  public List<FieldSession> listSessions(UUID actorId, UUID inspectionId) {
    ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User not found"));
    boolean isReviewer =
        actor.hasRole(Roles.MANAGER) || actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.CUSTOMER);

    if (!isReviewer) {
      boolean partnerAssignment = isInspectorPartner(actorId);
      UUID organizationId = requireInspectorOrganization(actorId, partnerAssignment);
      Inspection inspection =
          inspections
              .findAssignedInspectorInspection(
                  inspectionId, organizationId, actorId, partnerAssignment)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.NOT_FOUND,
                          "INSPECTION_NOT_FOUND",
                          "Inspection was not found."));
      if (!inspection.isInspectedBy(actorId)) {
        throw new BusinessException(
            HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found.");
      }
      return sessions.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
    }

    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found."));
    if (actor.organizationId() != null
        && !actor.organizationId().equals(inspection.getOrganizationId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "INSPECTION_SCOPE_DENIED", "Access denied");
    }
    return sessions.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
  }

  @Transactional
  public FieldSession abort(UUID inspectorId, UUID inspectionId, UUID sessionId, String reason) {
    boolean partnerAssignment = isInspectorPartner(inspectorId);
    UUID organizationId = requireInspectorOrganization(inspectorId, partnerAssignment);
    FieldSession session =
        requireOwnedOpenSession(
            inspectionId, sessionId, organizationId, inspectorId, partnerAssignment);

    try {
      session.abort(reason);
    } catch (IllegalArgumentException exception) {
      throw sessionFailure("ABORT_REASON_REQUIRED", exception.getMessage());
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, SESSION_NOT_IN_PROGRESS, exception.getMessage());
    }
    FieldSession savedSession =
        reopenInspection(session, organizationId, inspectorId, partnerAssignment);
    if (eventPublisher != null) {
      eventPublisher.publishEvent(
          new FieldSessionAbortedEvent(
              inspectionId, organizationId, sessionId, inspectorId, reason, Instant.now()));
    }
    return savedSession;
  }

  @Transactional
  public FieldSession complete(
      UUID inspectorId, UUID inspectionId, UUID sessionId, String outcomeNotes) {
    if (outcomeNotes == null || outcomeNotes.isBlank()) {
      throw sessionFailure(
          "SESSION_OUTCOME_REQUIRED", "Record the completed field-session outcome.");
    }
    boolean partnerAssignment = isInspectorPartner(inspectorId);
    UUID organizationId = requireInspectorOrganization(inspectorId, partnerAssignment);
    Inspection inspection =
        lockInspection(inspectionId, organizationId, inspectorId, partnerAssignment);
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
      FieldSession session, UUID organizationId, UUID inspectorId, boolean partnerAssignment) {
    inspections
        .findAssignedInspectorInspectionWithLock(
            session.getInspectionId(), organizationId, inspectorId, partnerAssignment)
        .ifPresent(
            inspection -> {
              if (inspection.getStatus() == InspectionStatus.IN_PROGRESS) {
                inspection.moveTo(InspectionStatus.SCHEDULED);
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
      UUID inspectionId,
      UUID sessionId,
      UUID organizationId,
      UUID inspectorId,
      boolean partnerAssignment) {
    Inspection inspection =
        inspections
            .findAssignedInspectorInspection(
                inspectionId, organizationId, inspectorId, partnerAssignment)
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

  private Inspection lockInspection(
      UUID inspectionId, UUID organizationId, UUID inspectorId, boolean partnerAssignment) {
    Inspection inspection =
        inspections
            .findAssignedInspectorInspectionWithLock(
                inspectionId, organizationId, inspectorId, partnerAssignment)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found."));
    entityManager.refresh(inspection, LockModeType.PESSIMISTIC_WRITE);
    return inspection;
  }

  private UUID requireInspectorOrganization(UUID inspectorId, boolean partnerAssignment) {
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
    if (partnerAssignment != user.isInspectorPartner()) {
      throw sessionFailure(SESSION_SCOPE_DENIED, "Inspector partner scope is not valid.");
    }
    return user.organizationId();
  }

  private boolean isInspectorPartner(UUID inspectorId) {
    return userAccess
        .findActiveUser(inspectorId)
        .map(UserAccess.ActiveUser::isInspectorPartner)
        .orElse(false);
  }

  private static BusinessException sessionFailure(String code, String detail) {
    return new BusinessException(HttpStatus.CONFLICT, code, detail);
  }
}
