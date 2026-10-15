package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.EvidenceQualityDecisionResponse;
import com.smartdroneinspection.inspections.domain.EvidenceQualityDecision;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.EvidenceQualityDecisionType;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.EvidenceQualityDecisionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
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
  private final UserAccess userAccess;

  public EvidenceQualityService(
      InspectionRepository inspections,
      EvidenceQualityDecisionRepository decisions,
      EvidenceService evidenceService,
      UserAccess userAccess) {
    this.inspections = inspections;
    this.decisions = decisions;
    this.evidenceService = evidenceService;
    this.userAccess = userAccess;
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
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
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

    EvidenceQualityDecision record =
        new EvidenceQualityDecision(
            inspectionId, fieldSessionId, decision, shotListComparison, limitationReason, actorId);
    return toResponse(decisions.saveAndFlush(record));
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
