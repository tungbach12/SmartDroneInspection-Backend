package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.EvidenceQualityDecisionResponse;
import com.smartdroneinspection.inspections.domain.EvidenceQualityDecision;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import com.smartdroneinspection.inspections.domain.enums.EvidenceQualityDecisionType;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.repository.EvidenceQualityDecisionRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.inspections.repository.InspectionReadinessDecisionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF3-03/04: the Inspector's substantive evidence decision. Technical validation and the AI model
 * cannot set this — only the assigned Inspector decides whether the set is adequate. An accepted
 * decision is what makes the evidence eligible for advisory detection and drafting.
 */
@Service
public class EvidenceQualityService {

  private final InspectionRepository inspections;
  private final EvidenceQualityDecisionRepository decisions;
  private final EvidenceService evidenceService;
  private final InspectionPreparationRepository preparations;
  private final InspectionReadinessDecisionRepository readinessDecisions;
  private final FieldSessionRepository sessions;
  private final EntityManager entityManager;

  public EvidenceQualityService(
      InspectionRepository inspections,
      EvidenceQualityDecisionRepository decisions,
      EvidenceService evidenceService,
      InspectionPreparationRepository preparations,
      InspectionReadinessDecisionRepository readinessDecisions,
      FieldSessionRepository sessions,
      EntityManager entityManager) {
    this.inspections = inspections;
    this.decisions = decisions;
    this.evidenceService = evidenceService;
    this.preparations = preparations;
    this.readinessDecisions = readinessDecisions;
    this.sessions = sessions;
    this.entityManager = entityManager;
  }

  @Transactional
  public EvidenceQualityDecisionResponse decide(
      UUID actorId,
      UUID inspectionId,
      UUID fieldSessionId,
      EvidenceQualityDecisionType decision,
      String shotListComparison,
      String limitationReason) {

    if (decision == null || decision == EvidenceQualityDecisionType.PENDING) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_QUALITY_INVALID",
          "A substantive evidence decision is required");
    }
    Inspection scopedInspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    Inspection inspection =
        inspections
            .findWithLockByIdAndOrganizationId(inspectionId, scopedInspection.getOrganizationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found."));
    entityManager.refresh(inspection, LockModeType.PESSIMISTIC_WRITE);
    if (!inspection.isInspectedBy(actorId)
        || inspection.getOrganizationId() == null
        || !inspection.getOrganizationId().equals(scopedInspection.getOrganizationId())) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found.");
    }
    if (inspection.getStatus() != InspectionStatus.FIELD_COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_STATE_CONFLICT",
          "Evidence can only be assessed after field work is completed");
    }
    // The comparison is what the report discloses about unobserved scope; a decision recorded
    // without it would leave the disclosure unsupported (Report 3 MF3-03). Scope and state are
    // checked first so an out-of-scope caller never learns input-validation details.
    if (shotListComparison == null || shotListComparison.isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_QUALITY_INVALID",
          "A shot-list comparison is required with every evidence decision");
    }
    // A decision that accepts or limits coverage must explain itself; the report reader needs to
    // know
    // what was and was not observed.
    if ((decision == EvidenceQualityDecisionType.LIMITED
            || decision == EvidenceQualityDecisionType.REUPLOAD_REQUIRED
            || decision == EvidenceQualityDecisionType.ADDITIONAL_SESSION_REQUIRED)
        && (limitationReason == null || limitationReason.isBlank())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_QUALITY_INVALID",
          "This decision requires a stated reason or limitation");
    }

    if (decision == EvidenceQualityDecisionType.ADDITIONAL_SESSION_REQUIRED) {
      // Everything is validated before anything is written, so a refusal leaves no decision row,
      // no preparation change and no inspection state change.
      AdditionalSessionHandoff handoff =
          validateAdditionalSession(actorId, inspectionId, fieldSessionId);
      EvidenceQualityDecision record =
          new EvidenceQualityDecision(
              inspectionId,
              handoff.completedSessionId(),
              decision,
              shotListComparison,
              limitationReason,
              actorId);
      EvidenceQualityDecision saved = decisions.saveAndFlush(record);
      applyAdditionalSession(inspection, handoff, actorId);
      return toResponse(saved);
    }

    EvidenceQualityDecision record =
        new EvidenceQualityDecision(
            inspectionId, fieldSessionId, decision, shotListComparison, limitationReason, actorId);
    return toResponse(decisions.saveAndFlush(record));
  }

  /**
   * The facts the additional-session handoff needs, resolved before any write.
   *
   * <p>The completed session anchors the decision to the visit it assesses, and the latest approved
   * preparation plus its approval are what the handoff supersedes.
   */
  private record AdditionalSessionHandoff(
      UUID completedSessionId,
      InspectionPreparation preparation,
      InspectionReadinessDecision approval) {}

  private AdditionalSessionHandoff validateAdditionalSession(
      UUID actorId, UUID inspectionId, UUID fieldSessionId) {
    FieldSession latestCompletedSession =
        sessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(session -> "FIELD_COMPLETED".equals(session.getStatus()))
            .filter(session -> session.getInspectorUserId().equals(actorId))
            .reduce((first, second) -> second)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "FIELD_SESSION_NOT_COMPLETED",
                        "An additional session can only be requested after a completed field session."));
    if (fieldSessionId != null && !fieldSessionId.equals(latestCompletedSession.getId())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FIELD_SESSION_NOT_CURRENT",
          "The evidence decision must refer to the latest completed field session.");
    }
    boolean hasEvidenceForSession =
        evidenceService.listForFieldSession(inspectionId, latestCompletedSession.getId()).stream()
            .anyMatch(item -> item.uploadStatus() == UploadStatus.AVAILABLE);
    if (!hasEvidenceForSession) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FIELD_SESSION_EVIDENCE_MISSING",
          "Upload evidence from the completed field session before assessing it.");
    }
    InspectionPreparation latestPreparation =
        preparations
            .findFirstByInspectionIdOrderByPreparationVersionDesc(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "PREPARATION_MISSING",
                        "An existing preparation is required before requesting another session."));
    latestPreparation =
        preparations
            .findWithLockByIdAndInspectionId(latestPreparation.getId(), inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "PREPARATION_NOT_CURRENT",
                        "The latest preparation could not be locked for the decision."));
    InspectionReadinessDecision currentReadiness =
        readinessDecisions.findFirstByInspectionIdOrderByDecidedAtDesc(inspectionId).orElse(null);
    if (latestPreparation.getStatus() != InspectionPreparationStatus.READY
        || !latestPreparation.getInspectorUserId().equals(actorId)
        || currentReadiness == null
        || currentReadiness.getDecision() != ReadinessDecisionType.APPROVED
        || !latestPreparation.getId().equals(currentReadiness.getPreparationId())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PREPARATION_NOT_CURRENT",
          "An additional session requires the latest approved preparation.");
    }
    return new AdditionalSessionHandoff(
        latestCompletedSession.getId(), latestPreparation, currentReadiness);
  }

  /**
   * Applies the handoff: the prior approval is invalidated rather than rewritten, the approved
   * preparation is superseded, a next-version draft is opened and the inspection returns to
   * preparation. No field session, readiness approval, report version or evidence row is created.
   */
  private void applyAdditionalSession(
      Inspection inspection, AdditionalSessionHandoff handoff, UUID actorId) {
    InspectionPreparation preparation = handoff.preparation();
    InspectionReadinessDecision approval = handoff.approval();
    preparation.markSuperseded();
    preparations.saveAndFlush(preparation);
    readinessDecisions.saveAndFlush(
        new InspectionReadinessDecision(
            inspection.getId(),
            preparation.getId(),
            preparation.getPreparationVersion(),
            ReadinessDecisionType.INVALIDATED,
            actorId,
            "Inspector requested an additional field session",
            approval.getPermitSnapshot(),
            approval.getCredentialSnapshot(),
            approval.getDroneDocumentSnapshot(),
            approval.getPermitSnapshotIds(),
            approval.getCredentialSnapshotIds(),
            approval.getDroneDocumentSnapshotIds(),
            approval.getSourceHash()));
    try {
      inspection.requestAdditionalSession();
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", exception.getMessage());
    }
    preparations.saveAndFlush(
        preparation.nextDraftVersion(preparation.getPreparationVersion() + 1));
    inspections.saveAndFlush(inspection);
  }

  @Transactional(readOnly = true)
  public List<EvidenceQualityDecisionResponse> history(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return decisions.findByInspectionIdOrderByDecidedAtDesc(inspectionId).stream()
        .map(this::toResponse)
        .toList();
  }

  /**
   * Eligibility gate for advisory detection (MF3-05). Detection never runs on evidence the
   * Inspector has not accepted, so a model never sees data the Inspector rejected.
   */
  @Transactional(readOnly = true)
  public boolean isEligibleForAnalysis(UUID inspectionId) {
    return decisions
        .findFirstByInspectionIdOrderByDecidedAtDesc(inspectionId)
        .map(EvidenceQualityDecision::isAccepted)
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public EvidenceQualityDecisionResponse current(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return decisions
        .findFirstByInspectionIdOrderByDecidedAtDesc(inspectionId)
        .map(this::toResponse)
        .orElse(null);
  }

  private EvidenceQualityDecisionResponse toResponse(EvidenceQualityDecision record) {
    return new EvidenceQualityDecisionResponse(
        record.getId(),
        record.getInspectionId(),
        record.getFieldSessionId(),
        record.getDecision(),
        record.getShotListComparison(),
        record.getLimitationReason(),
        record.getDecidedByUserId(),
        record.getDecidedAt());
  }
}
