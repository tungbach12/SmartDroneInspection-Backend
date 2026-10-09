package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.ManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewCandidateRequest;
import com.smartdroneinspection.inspections.api.dto.response.AiFindingCandidateResponse;
import com.smartdroneinspection.inspections.api.dto.response.VerifiedFindingResponse;
import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import com.smartdroneinspection.inspections.domain.enums.FindingDecision;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingSource;
import com.smartdroneinspection.inspections.repository.AiFindingCandidateRepository;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  private final Optional<AiInferencePort> inference;

  public FindingService(
      EvidenceRepository evidence,
      AiFindingCandidateRepository candidates,
      VerifiedFindingRepository findings,
      EvidenceService evidenceService,
      EvidenceQualityService qualityService,
      Optional<AiInferencePort> inference) {
    this.evidence = evidence;
    this.candidates = candidates;
    this.findings = findings;
    this.evidenceService = evidenceService;
    this.qualityService = qualityService;
    this.inference = inference;
  }

  /** MF3-05: run advisory detection. Refuses evidence the Inspector has not accepted. */
  @Transactional
  public List<AiFindingCandidateResponse> analyze(
      UUID actorId, UUID inspectionId, UUID evidenceId) {
    evidenceService.requireAssignedInspection(actorId, inspectionId);

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
    return candidates.saveAll(created).stream().map(this::toCandidateResponse).toList();
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
    evidenceService.requireAssignedInspection(actorId, inspectionId);

    AiFindingCandidate candidate =
        candidates
            .findByIdAndInspectionId(candidateId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "AI_CANDIDATE_NOT_FOUND", "Candidate not found"));

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
    return Optional.of(toFindingResponse(findings.saveAndFlush(finding)));
  }

  /** MF3-06: a manual finding is available whenever detection is unavailable or missed a defect. */
  @Transactional
  public VerifiedFindingResponse createManualFinding(
      UUID actorId, UUID inspectionId, ManualFindingRequest request) {
    evidenceService.requireAssignedInspection(actorId, inspectionId);

    Evidence record =
        request.evidenceId() == null ? null : requireEvidence(inspectionId, request.evidenceId());

    VerifiedFinding finding =
        new VerifiedFinding(
            inspectionId,
            record == null ? null : record.getId(),
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
    finding.recordDecision(decision, actorId, rationale);
    return toFindingResponse(findings.saveAndFlush(finding));
  }

  @Transactional(readOnly = true)
  public List<VerifiedFindingResponse> listFindings(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .map(this::toFindingResponse)
        .toList();
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
        finding.isRepairRequired(),
        finding.getStatus());
  }
}
