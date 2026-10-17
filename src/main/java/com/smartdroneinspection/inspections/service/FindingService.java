package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.ManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewCandidateRequest;
import com.smartdroneinspection.inspections.api.dto.response.AiFindingCandidateResponse;
import com.smartdroneinspection.inspections.api.dto.response.VerifiedFindingResponse;
import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import com.smartdroneinspection.inspections.domain.enums.FindingDecision;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingSource;
import com.smartdroneinspection.inspections.repository.AiFindingCandidateRepository;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportVersionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF3-05/06/09: advisory detection and human finding decisions.
 *
 * <p>Candidates stay non-official. A candidate becomes a finding only through an explicit human
 * decision, and an inference outage degrades the workflow rather than stopping it — evidence stays
 * usable and the manual finding path stays open.
 */
@Service
public class FindingService {

  private final EvidenceRepository evidence;
  private final AiFindingCandidateRepository candidates;
  private final VerifiedFindingRepository findings;
  private final EvidenceService evidenceService;
  private final EvidenceQualityService qualityService;
  private final InspectionReportRepository reports;
  private final InspectionReportVersionRepository reportVersions;
  private final InspectionRepository inspections;
  private final Optional<AiInferencePort> inference;

  public FindingService(
      EvidenceRepository evidence,
      AiFindingCandidateRepository candidates,
      VerifiedFindingRepository findings,
      EvidenceService evidenceService,
      EvidenceQualityService qualityService,
      InspectionReportRepository reports,
      InspectionReportVersionRepository reportVersions,
      InspectionRepository inspections,
      Optional<AiInferencePort> inference) {
    this.evidence = evidence;
    this.candidates = candidates;
    this.findings = findings;
    this.evidenceService = evidenceService;
    this.qualityService = qualityService;
    this.reports = reports;
    this.reportVersions = reportVersions;
    this.inspections = inspections;
    this.inference = inference;
  }

  /** MF3-06: explicitly confirm the resolved findings set before entering report drafting. */
  @Transactional
  public void confirmFindings(UUID actorId, UUID inspectionId, boolean noFindingsAcknowledged) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    if (inspection.getStatus()
            != com.smartdroneinspection.inspections.domain.enums.InspectionStatus.FIELD_COMPLETED
        && inspection.getStatus()
            != com.smartdroneinspection.inspections.domain.enums.InspectionStatus.REPORT_DRAFT) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_STATE_CONFLICT",
          "Findings can only be confirmed after field work is completed");
    }
    if (!qualityService.isEligibleForAnalysis(inspectionId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVIDENCE_NOT_ACCEPTED",
          "Findings confirmation requires an accepted evidence set");
    }
    long pending =
        candidates.countByInspectionIdAndStatus(inspectionId, AiFindingCandidateStatus.PENDING);
    if (pending > 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PENDING_CANDIDATES_EXIST",
          "Resolve every AI candidate before confirming findings");
    }
    long findingCount = findings.countByInspectionId(inspectionId);
    if (findingCount == 0 && !noFindingsAcknowledged) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_FINDINGS_ACKNOWLEDGMENT_REQUIRED",
          "Confirm that no finding was observed within the inspected scope");
    }
    String snapshot = findingsSnapshotHash(inspectionId);
    if (inspection.getStatus()
        == com.smartdroneinspection.inspections.domain.enums.InspectionStatus.REPORT_DRAFT) {
      if (inspection.hasCurrentFindingsConfirmation(snapshot)) return;
      rejectLockedFindingEdits(inspectionId);
      inspection.reopenFindings();
      inspections.saveAndFlush(inspection);
    }
    try {
      inspection.confirmFindings(actorId, snapshot);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", ex.getMessage());
    }
    inspections.saveAndFlush(inspection);
  }

  @Transactional
  public void reopenFindings(UUID actorId, UUID inspectionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    if (inspection.getStatus()
        != com.smartdroneinspection.inspections.domain.enums.InspectionStatus.REPORT_DRAFT) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", "Findings are not currently confirmed");
    }
    inspection.reopenFindings();
    inspections.saveAndFlush(inspection);
  }

  /** MF3-05: run advisory detection. Refuses evidence the Inspector has not accepted. */
  @Transactional
  public List<AiFindingCandidateResponse> analyze(
      UUID actorId, UUID inspectionId, UUID evidenceId, boolean replace) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    if (inspection.getStatus()
        != com.smartdroneinspection.inspections.domain.enums.InspectionStatus.FIELD_COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "FINDINGS_LOCKED", "Reopen Findings before running analysis");
    }

    Evidence record = requireEvidence(inspectionId, evidenceId);
    if (!record.isImage()) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "AI_INFERENCE_UNAVAILABLE",
          "Only image evidence can be analyzed automatically");
    }
    if (!qualityService.isEligibleForAnalysis(inspectionId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "AI_INFERENCE_UNAVAILABLE",
          "Analysis requires an accepted evidence quality decision");
    }
    AiInferencePort port =
        inference.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_INFERENCE_UNAVAILABLE",
                    "Automated analysis is not configured"));

    // MF3-05 runs automatically once the evidence set is accepted, so re-running it on the same
    // capture must not create a second candidate for the same detection. An explicit re-run
    // replaces
    // the image's unreviewed suggestions instead, which is what a changed detector or threshold
    // needs; a candidate the Inspector already reviewed carries that decision and survives.
    List<AiFindingCandidate> prior = candidates.findByEvidenceIdOrderByCreatedAtAsc(evidenceId);
    if (!replace) {
      if (!prior.isEmpty()) {
        return prior.stream().map(this::toCandidateResponse).toList();
      }
    }
    List<AiFindingCandidate> reviewed =
        replace
            ? prior.stream()
                .filter(candidate -> candidate.getStatus() != AiFindingCandidateStatus.PENDING)
                .toList()
            : List.of();

    byte[] bytes;
    try (InputStream stream = evidenceService.readForAnalysis(inspectionId, evidenceId).stream()) {
      bytes = stream.readAllBytes();
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }

    List<AiInferencePort.Detection> detections;
    try {
      detections = port.analyze(bytes, record.getContentType());
    } catch (IOException ex) {
      // Evidence survives an inference outage, and the manual path stays available.
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI_INFERENCE_UNAVAILABLE",
          "Automated analysis is temporarily unavailable");
    }

    List<AiFindingCandidate> created =
        detections.stream()
            .map(
                detection ->
                    new AiFindingCandidate(
                        evidenceId,
                        inspectionId,
                        detection.modelName(),
                        detection.modelVersion(),
                        null,
                        detection.predictedLabel(),
                        detection.confidence(),
                        detection.boundingBox()))
            .toList();
    if (replace) {
      // Only after the new detections exist, so a failed inference leaves the previous suggestions
      // in place rather than emptying the image.
      List<AiFindingCandidate> replaced =
          prior.stream()
              .filter(candidate -> candidate.getStatus() == AiFindingCandidateStatus.PENDING)
              .toList();
      if (!replaced.isEmpty()) {
        candidates.deleteAll(replaced);
      }
    }
    List<AiFindingCandidate> stored = candidates.saveAll(created);
    return Stream.concat(stored.stream(), reviewed.stream())
        .map(this::toCandidateResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AiFindingCandidateResponse> listCandidates(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return candidates.findByInspectionIdOrderByCreatedAtDesc(inspectionId).stream()
        .map(this::toCandidateResponse)
        .toList();
  }

  /**
   * Confirming or modifying a candidate records the human decision and produces the official
   * finding. A rejection records a reason and produces nothing.
   */
  @Transactional
  public Optional<VerifiedFindingResponse> reviewCandidate(
      UUID actorId, UUID inspectionId, UUID candidateId, ReviewCandidateRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    reopenFindings(inspection);

    AiFindingCandidate candidate =
        candidates
            .findByIdAndInspectionId(candidateId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "AI_CANDIDATE_NOT_FOUND", "Candidate not found"));
    if (candidate.getStatus() != AiFindingCandidateStatus.PENDING) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CANDIDATE_ALREADY_REVIEWED",
          "Use the reviewed-candidate revision action to change this decision");
    }

    candidate.review(request.decision(), actorId, request.reason());
    candidates.saveAndFlush(candidate);

    if (request.decision() == AiFindingCandidateStatus.REJECTED) {
      return Optional.empty();
    }

    VerifiedFindingSource source =
        request.decision() == AiFindingCandidateStatus.CONFIRMED
            ? VerifiedFindingSource.AI_CONFIRMED
            : VerifiedFindingSource.AI_MODIFIED;

    // Re-deciding the same candidate reuses the finding rather than duplicating it.
    VerifiedFinding finding =
        findings
            .findByInspectionIdAndAiCandidateId(inspectionId, candidateId)
            .orElseGet(
                () ->
                    new VerifiedFinding(
                        inspectionId,
                        candidate.getEvidenceId(),
                        candidateId,
                        actorId,
                        source,
                        nextFindingCode(inspectionId),
                        orDefault(request.defectLabel(), candidate.getPredictedLabel()),
                        request.severity(),
                        request.locationDescription(),
                        orDefault(request.technicalNotes(), candidate.getPredictedLabel()),
                        request.recommendedAction(),
                        candidate.getBoundingBox(),
                        request.component(),
                        request.description(),
                        request.observedCondition(),
                        request.priority(),
                        request.measurement(),
                        Boolean.TRUE.equals(request.repairRequired())));
    finding.recordInspectorProposal(
        source,
        orDefault(request.defectLabel(), candidate.getPredictedLabel()),
        request.severity(),
        request.locationDescription(),
        orDefault(request.technicalNotes(), candidate.getPredictedLabel()),
        request.recommendedAction(),
        request.component(),
        request.description(),
        request.observedCondition(),
        request.priority(),
        request.measurement(),
        Boolean.TRUE.equals(request.repairRequired()));
    return Optional.of(toFindingResponse(findings.saveAndFlush(finding)));
  }

  /** Revises an Inspector-reviewed candidate before reviewer decision and report lock. */
  @Transactional
  public Optional<VerifiedFindingResponse> reviseReviewedCandidate(
      UUID actorId, UUID inspectionId, UUID candidateId, ReviewCandidateRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    reopenFindings(inspection);
    AiFindingCandidate candidate =
        candidates
            .findByIdAndInspectionId(candidateId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "AI_CANDIDATE_NOT_FOUND", "Candidate not found"));
    if (!actorId.equals(candidate.getReviewedByUserId())) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "CANDIDATE_REVIEWER_INVALID",
          "Only the Inspector who reviewed the candidate may revise it");
    }
    if (candidate.getStatus() == AiFindingCandidateStatus.PENDING) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CANDIDATE_REVISION_LOCKED",
          "Only a reviewed candidate can be revised");
    }
    VerifiedFinding linked =
        findings.findByInspectionIdAndAiCandidateId(inspectionId, candidateId).orElse(null);
    if (linked != null && linked.getDecision() != null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CANDIDATE_REVISION_LOCKED",
          "A reviewer has decided the linked finding");
    }
    var report = reports.findByInspectionId(inspectionId).orElse(null);
    if (report != null
        && reportVersions.findByInspectionReportIdOrderByVersionNoDesc(report.getId()).stream()
            .anyMatch(
                version ->
                    version.getStatus() == ReportStatus.SUBMITTED
                        || version.getStatus() == ReportStatus.APPROVED
                        || version.getStatus() == ReportStatus.PUBLISHED
                        || version.getStatus() == ReportStatus.SUPERSEDED)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CANDIDATE_REVISION_LOCKED",
          "A downstream report decision or publication locks candidate revisions");
    }
    candidate.reviseReview(request.decision(), request.reason());
    candidates.saveAndFlush(candidate);
    if (request.decision() == AiFindingCandidateStatus.REJECTED) {
      if (linked != null) findings.delete(linked);
      return Optional.empty();
    }
    VerifiedFindingSource source =
        request.decision() == AiFindingCandidateStatus.CONFIRMED
            ? VerifiedFindingSource.AI_CONFIRMED
            : VerifiedFindingSource.AI_MODIFIED;
    if (linked == null) {
      linked =
          new VerifiedFinding(
              inspectionId,
              candidate.getEvidenceId(),
              candidateId,
              actorId,
              source,
              nextFindingCode(inspectionId),
              orDefault(request.defectLabel(), candidate.getPredictedLabel()),
              request.severity(),
              request.locationDescription(),
              orDefault(request.technicalNotes(), candidate.getPredictedLabel()),
              request.recommendedAction(),
              candidate.getBoundingBox(),
              request.component(),
              request.description(),
              request.observedCondition(),
              request.priority(),
              request.measurement(),
              Boolean.TRUE.equals(request.repairRequired()));
    } else {
      linked.recordInspectorProposal(
          source,
          orDefault(request.defectLabel(), candidate.getPredictedLabel()),
          request.severity(),
          request.locationDescription(),
          orDefault(request.technicalNotes(), candidate.getPredictedLabel()),
          request.recommendedAction(),
          request.component(),
          request.description(),
          request.observedCondition(),
          request.priority(),
          request.measurement(),
          Boolean.TRUE.equals(request.repairRequired()));
    }
    return Optional.of(toFindingResponse(findings.saveAndFlush(linked)));
  }

  /** MF3-06: a manual finding is available whenever detection is unavailable or missed a defect. */
  @Transactional
  public VerifiedFindingResponse createManualFinding(
      UUID actorId, UUID inspectionId, ManualFindingRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    reopenFindings(inspection);

    Evidence record = requireEvidence(inspectionId, request.evidenceId());

    VerifiedFinding finding =
        new VerifiedFinding(
            inspectionId,
            record.getId(),
            null,
            actorId,
            VerifiedFindingSource.MANUAL,
            nextFindingCode(inspectionId),
            request.defectLabel(),
            request.severity(),
            request.locationDescription(),
            request.technicalNotes(),
            request.recommendedAction(),
            null,
            request.component(),
            request.description(),
            request.observedCondition(),
            request.priority(),
            request.measurement(),
            Boolean.TRUE.equals(request.repairRequired()));
    finding.recordInspectorProposal(
        VerifiedFindingSource.MANUAL,
        request.defectLabel(),
        request.severity(),
        request.locationDescription(),
        request.technicalNotes(),
        request.recommendedAction(),
        request.component(),
        request.description(),
        request.observedCondition(),
        request.priority(),
        request.measurement(),
        Boolean.TRUE.equals(request.repairRequired()));
    return toFindingResponse(findings.saveAndFlush(finding));
  }

  /** MF3-09: the reviewer records the final decision on a finding. */
  @Transactional
  public VerifiedFindingResponse decideFinding(
      UUID actorId, UUID inspectionId, UUID findingId, FindingDecision decision, String rationale) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    VerifiedFinding finding =
        findings
            .findByIdAndInspectionId(findingId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "FINDING_NOT_FOUND", "Finding not found"));
    // Separation of duties takes precedence over workflow state: an Inspector who is also an
    // ORG_ADMIN must never be able to decide their own proposal, even before report submission.
    if (finding.getCreatedByUserId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FINDING_REVIEWER_INVALID",
          "The finding author cannot decide their own finding");
    }
    var report = reports.findByInspectionId(inspectionId).orElse(null);
    if (report == null || !ReportStatus.SUBMITTED.name().equals(report.getStatus())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_STATE_CONFLICT",
          "Findings are reviewed as part of a submitted report");
    }
    if (decision == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "FINDING_DECISION_INVALID", "A finding decision is required");
    }
    if (rationale == null || rationale.isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "FINDING_DECISION_INVALID", "A decision rationale is required");
    }
    try {
      finding.recordDecision(decision, actorId, rationale);
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "FINDING_DECISION_INVALID", ex.getMessage());
    }
    return toFindingResponse(findings.saveAndFlush(finding));
  }

  /** MF3-12: the reviewer records a corrective outcome after report publication. */
  @Transactional
  public VerifiedFindingResponse recordCorrectiveAction(
      UUID actorId,
      UUID inspectionId,
      UUID findingId,
      boolean correctiveActionRequired,
      String priority,
      java.time.Instant dueAt,
      String rationale) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    var report = reports.findByInspectionId(inspectionId).orElse(null);
    VerifiedFinding finding =
        findings
            .findByIdAndInspectionId(findingId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "FINDING_NOT_FOUND", "Finding not found"));
    if (!finding.isOfficial()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDING_REVIEW_INCOMPLETE",
          "Corrective action can only be recorded for a confirmed or modified finding");
    }
    var inspection = inspections.findById(inspectionId).orElse(null);
    if (inspection != null && inspection.getCorrectiveHandoffAt() != null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CORRECTIVE_HANDOFF_ALREADY_COMPLETED",
          "Corrective handoff has already been completed");
    }
    if (!ReportStatus.PUBLISHED.name().equals(report == null ? null : report.getStatus())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_STATE_CONFLICT",
          "Corrective actions are recorded after the report is published");
    }
    boolean publishedVersionExists =
        report != null
            && reportVersions.findByInspectionReportIdOrderByVersionNoDesc(report.getId()).stream()
                .anyMatch(version -> version.getStatus() == ReportStatus.PUBLISHED);
    if (finding.isCorrectiveActionReviewed()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CORRECTIVE_ACTION_ALREADY_RECORDED",
          "Corrective action has already been recorded for this published finding");
    }
    if (!publishedVersionExists) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_STATE_CONFLICT",
          "Corrective actions are recorded after the report is published");
    }
    try {
      finding.updateCorrectiveAction(actorId, correctiveActionRequired, priority, dueAt, rationale);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "CORRECTIVE_ACTION_INVALID", ex.getMessage());
    }
    return toFindingResponse(findings.saveAndFlush(finding));
  }

  @Transactional(readOnly = true)
  public List<VerifiedFindingResponse> listFindings(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .map(this::toFindingResponse)
        .toList();
  }

  String findingsSnapshotHash(UUID inspectionId) {
    return FindingsSnapshot.hash(
        evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId),
        candidates.findByInspectionIdOrderByCreatedAtDesc(inspectionId),
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId));
  }

  private void rejectLockedFindingEdits(UUID inspectionId) {
    var report = reports.findByInspectionId(inspectionId).orElse(null);
    if (report != null
        && reportVersions.findByInspectionReportIdOrderByVersionNoDesc(report.getId()).stream()
            .anyMatch(
                version ->
                    version.getStatus() == ReportStatus.SUBMITTED
                        || version.getStatus() == ReportStatus.APPROVED
                        || version.getStatus() == ReportStatus.PUBLISHED
                        || version.getStatus() == ReportStatus.SUPERSEDED)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_LOCKED",
          "Submitted or reviewed report findings cannot be changed");
    }
  }

  private void reopenFindings(Inspection inspection) {
    if (inspection.getStatus()
        != com.smartdroneinspection.inspections.domain.enums.InspectionStatus.REPORT_DRAFT) {
      return;
    }
    rejectLockedFindingEdits(inspection.getId());
    inspection.reopenFindings();
    inspections.saveAndFlush(inspection);
  }

  private Evidence requireEvidence(UUID inspectionId, UUID evidenceId) {
    return evidence
        .findByIdAndInspectionId(evidenceId, inspectionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Evidence not found"));
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String nextFindingCode(UUID inspectionId) {
    return "F-" + (findings.countByInspectionId(inspectionId) + 1);
  }

  private AiFindingCandidateResponse toCandidateResponse(AiFindingCandidate candidate) {
    return new AiFindingCandidateResponse(
        candidate.getId(),
        candidate.getEvidenceId(),
        candidate.getModelName(),
        candidate.getModelVersion(),
        candidate.getPredictedLabel(),
        candidate.getConfidence(),
        candidate.getBoundingBox(),
        candidate.getStatus(),
        candidate.getReviewedByUserId(),
        candidate.getReviewedAt(),
        candidate.getRejectionReason(),
        candidate.getCreatedAt());
  }

  private VerifiedFindingResponse toFindingResponse(VerifiedFinding finding) {
    return new VerifiedFindingResponse(
        finding.getId(),
        finding.getInspectionId(),
        finding.getEvidenceId(),
        finding.getAiCandidateId(),
        finding.getSource(),
        finding.getFindingCode(),
        finding.getDefectLabel(),
        finding.getSeverity(),
        finding.getLocationDescription(),
        finding.getTechnicalNotes(),
        finding.getRecommendedAction(),
        finding.getComponent(),
        finding.getDescription(),
        finding.getObservedCondition(),
        finding.getPriority(),
        finding.getMeasurement(),
        finding.getDecision(),
        finding.getDecidedByUserId(),
        finding.getDecidedAt(),
        finding.isRepairRequired(),
        finding.getStatus(),
        finding.isCorrectiveActionRequired(),
        finding.getCorrectiveActionPriority(),
        finding.getCorrectiveActionDueAt(),
        finding.getCorrectiveActionReviewedByUserId(),
        finding.getCorrectiveActionReviewedAt(),
        finding.getCorrectiveActionRationale());
  }
}
