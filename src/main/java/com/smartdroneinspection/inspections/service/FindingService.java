package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.ManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateFindingRequest;
import com.smartdroneinspection.inspections.api.dto.response.FindingResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportVersionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
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
 * MF3-05/06/09: advisory detection and the Inspector's finding decisions.
 *
 * <p>A finding is one observation, whoever made it. The detector proposes, the Inspector records
 * what they saw, and the question the workflow asks of both is the same: has the Inspector
 * confirmed it. A finding that has not been confirmed is not official, so it does not enter a
 * report and it does not carry a corrective outcome.
 *
 * <p>Removing a finding hides it rather than erasing it, and it can be brought back. An inference
 * outage degrades the workflow rather than stopping it - the evidence stays usable and the manual
 * path stays open.
 */
@Service
public class FindingService {

  private final EvidenceRepository evidence;
  private final FindingRepository findings;
  private final EvidenceService evidenceService;
  private final InspectionReportVersionRepository reportVersions;
  private final InspectionRepository inspections;
  private final Optional<AiInferencePort> inference;
  private final UserAccess userAccess;

  public FindingService(
      EvidenceRepository evidence,
      FindingRepository findings,
      EvidenceService evidenceService,
      InspectionReportVersionRepository reportVersions,
      InspectionRepository inspections,
      Optional<AiInferencePort> inference,
      UserAccess userAccess) {
    this.evidence = evidence;
    this.findings = findings;
    this.evidenceService = evidenceService;
    this.reportVersions = reportVersions;
    this.inspections = inspections;
    this.inference = inference;
    this.userAccess = userAccess;
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
    boolean hasActiveEvidence =
        evidence.existsByInspectionIdAndRetiredAtIsNullAndUploadStatus(
            inspectionId, UploadStatus.AVAILABLE);
    if (!hasActiveEvidence) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ACTIVE_EVIDENCE_REQUIRED",
          "Upload at least one active evidence item before confirming the findings set");
    }
    // A detector suggestion is a proposal the Inspector has to accept or remove, so it blocks
    // until it is decided. A finding the Inspector recorded themselves is not a proposal: the
    // aggregate confirmation below is the only step it still needs.
    long undecidedSuggestions =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(finding -> finding.getStatus() == FindingStatus.PENDING)
            .filter(finding -> finding.getModelName() != null)
            .count();
    if (undecidedSuggestions > 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PENDING_FINDINGS_EXIST",
          "Confirm or remove every suggested finding before confirming the set");
    }
    List<Finding> inspectorFindings = findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    long findingCount = inspectorFindings.size();
    if (findingCount == 0 && !noFindingsAcknowledged) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_FINDINGS_ACKNOWLEDGMENT_REQUIRED",
          "Confirm that no finding was observed within the inspected scope");
    }
    // The aggregate confirmation is what makes the set official, so a finding that has not been
    // confirmed yet is confirmed here.
    for (Finding finding : inspectorFindings) {
      finding.confirmByInspector();
    }
    if (!inspectorFindings.isEmpty()) {
      findings.saveAllAndFlush(inspectorFindings);
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
      inspection.confirmFindings(snapshot);
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
    if (inspection.hasFindingsConfirmation()) {
      findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
          .filter(Finding::isOfficial)
          .forEach(Finding::clearInspectorConfirmation);
      findings.flush();
    }
    inspection.reopenFindings();
    inspections.saveAndFlush(inspection);
  }

  /** MF3-05: run advisory detection. Refuses evidence the Inspector has not accepted. */
  @Transactional
  public List<FindingResponse> analyze(
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
    AiInferencePort port =
        inference.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_INFERENCE_UNAVAILABLE",
                    "Automated analysis is not configured"));

    // MF3-05 is invoked explicitly by the Inspector; re-running the same capture must not create a
    // second finding for the same detection. An explicit re-run replaces
    // the image's unreviewed suggestions instead, which is what a changed detector or threshold
    // needs; a finding the Inspector already reviewed carries that decision and survives.
    List<Finding> prior = findings.findByEvidenceIdOrderByCreatedAtAsc(evidenceId);
    if (!replace && !prior.isEmpty()) {
      return prior.stream().map(this::toFindingResponse).toList();
    }
    List<Finding> reviewed =
        replace
            ? prior.stream()
                .filter(finding -> finding.getStatus() != FindingStatus.PENDING)
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
      detections =
          userAccess.isVisionFallbackEnabled(actorId)
              ? port.analyze(bytes, record.getContentType())
              : port.analyze(bytes, record.getContentType(), false);
    } catch (IOException ex) {
      // Evidence survives an inference outage, and the manual path stays available.
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI_INFERENCE_UNAVAILABLE",
          "Automated analysis is temporarily unavailable");
    }

    List<Finding> created =
        detections.stream()
            .map(
                detection ->
                    new Finding(
                        inspectionId,
                        evidenceId,
                        nextFindingCode(inspectionId),
                        detection.predictedLabel(),
                        FindingSeverity.MEDIUM,
                        detection.predictedLabel(),
                        null,
                        detection.boundingBox(),
                        detection.modelName(),
                        detection.modelVersion(),
                        detection.provider(),
                        detection.confidence()))
            .toList();
    if (replace) {
      // Only after the new detections exist, so a failed inference leaves the previous suggestions
      // in place rather than emptying the image.
      List<Finding> replaced =
          prior.stream().filter(finding -> finding.getStatus() == FindingStatus.PENDING).toList();
      if (!replaced.isEmpty()) {
        findings.deleteAll(replaced);
      }
    }
    List<Finding> stored = findings.saveAll(created);
    return Stream.concat(stored.stream(), reviewed.stream()).map(this::toFindingResponse).toList();
  }

  /**
   * The findings of an inspection.
   *
   * <p>Removed findings are kept out of the working set. A caller that wants to see them asks for
   * them through {@link #listRemovedFindings}.
   */
  @Transactional(readOnly = true)
  public List<FindingResponse> listFindings(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .map(this::toFindingResponse)
        .toList();
  }

  /** Explicitly deleted Inspector findings, retained with actor/time/reason attribution. */
  @Transactional(readOnly = true)
  public List<FindingResponse> listRemovedFindings(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return findings.findRemovedByInspectionId(inspectionId).stream()
        .map(this::toFindingResponse)
        .toList();
  }

  /**
   * MF3-06: the Inspector accepts a finding and states what it is.
   *
   * <p>The finding becomes official. Confirming one that was already confirmed is refused, because
   * the content would change underneath a decision that was already made.
   */
  @Transactional
  public FindingResponse confirmFinding(
      UUID actorId, UUID inspectionId, UUID findingId, ReviewFindingRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    Finding finding = requireFinding(inspectionId, findingId);
    if (finding.getStatus() == FindingStatus.CONFIRMED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDING_ALREADY_CONFIRMED",
          "This finding has already been confirmed");
    }
    reopenFindings(inspection);

    finding.revise(
        request.evidenceId() == null ? finding.getEvidenceId() : request.evidenceId(),
        orDefault(request.defectLabel(), finding.getDefectLabel()),
        request.severity() == null ? finding.getSeverity() : request.severity(),
        orDefault(request.technicalNotes(), finding.getTechnicalNotes()),
        request.recommendedAction() == null
            ? finding.getRecommendedAction()
            : request.recommendedAction());
    finding.confirmByInspector();
    return toFindingResponse(findings.saveAndFlush(finding));
  }

  /** MF3-06: a manual finding is available whenever detection is unavailable or missed a defect. */
  @Transactional
  public FindingResponse createManualFinding(
      UUID actorId, UUID inspectionId, ManualFindingRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    reopenFindings(inspection);

    Evidence record = requireEvidence(inspectionId, request.evidenceId());

    Finding finding =
        new Finding(
            inspectionId,
            record.getId(),
            nextFindingCode(inspectionId),
            request.defectLabel(),
            request.severity(),
            request.technicalNotes(),
            request.recommendedAction(),
            null,
            null,
            null,
            null,
            null);
    return toFindingResponse(findings.saveAndFlush(finding));
  }

  /** MF3-06: correct an Inspector's finding while the current report is editable. */
  @Transactional
  public FindingResponse updateFinding(
      UUID actorId, UUID inspectionId, UUID findingId, UpdateFindingRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    Finding finding = requireFinding(inspectionId, findingId);
    Evidence record = requireEvidence(inspectionId, request.evidenceId());
    finding.revise(
        record.getId(),
        request.defectLabel(),
        request.severity(),
        request.technicalNotes(),
        request.recommendedAction());
    reopenFindings(inspection);
    return toFindingResponse(findings.saveAndFlush(finding));
  }

  /**
   * MF3-06: remove a finding from the working set.
   *
   * <p>A pending AI suggestion is rejected and removed from retained finding records. Inspector-
   * authored findings and confirmed findings keep the attributable deletion tombstone.
   */
  @Transactional
  public void deleteFinding(UUID actorId, UUID inspectionId, UUID findingId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    Finding finding = requireFinding(inspectionId, findingId);
    if (finding.isPendingAiSuggestion()) {
      findings.deleteByIdAndInspectionId(findingId, inspectionId);
      findings.flush();
      reopenFindings(inspection);
      return;
    }
    try {
      finding.delete();
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "FINDING_DELETE_INVALID", ex.getMessage());
    }
    findings.saveAndFlush(finding);
    reopenFindings(inspection);
  }

  /** Puts a removed finding back into the working set. */
  @Transactional
  public FindingResponse restoreFinding(UUID actorId, UUID inspectionId, UUID findingId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    Finding finding =
        findings.findRemovedByInspectionId(inspectionId).stream()
            .filter(item -> item.getId().equals(findingId))
            .findFirst()
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "FINDING_NOT_FOUND", "Finding not found"));
    try {
      finding.restore();
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "FINDING_RESTORE_INVALID", ex.getMessage());
    }
    reopenFindings(inspection);
    return toFindingResponse(findings.saveAndFlush(finding));
  }

  String findingsSnapshotHash(UUID inspectionId) {
    List<Evidence> currentEvidence =
        evidence.findByInspectionIdAndRetiredAtIsNullOrderByCreatedAtAsc(inspectionId);
    return FindingsSnapshot.hash(
        currentEvidence, findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId));
  }

  private void rejectLockedFindingEdits(UUID inspectionId) {
    var current =
        reportVersions.findFirstByInspectionIdOrderByVersionNoDesc(inspectionId).orElse(null);
    if (current != null
        && (current.getStatus() == ReportStatus.SUBMITTED
            || current.getStatus() == ReportStatus.APPROVED
            || current.getStatus() == ReportStatus.PUBLISHED
            || current.getStatus() == ReportStatus.SUPERSEDED)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_LOCKED",
          "Findings are locked while a report is submitted or returned for text revision");
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

  private Finding requireFinding(UUID inspectionId, UUID findingId) {
    return findings
        .findByIdAndInspectionId(findingId, inspectionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "FINDING_NOT_FOUND", "Finding not found"));
  }

  private Evidence requireEvidence(UUID inspectionId, UUID evidenceId) {
    Evidence record =
        evidence
            .findByIdAndInspectionId(evidenceId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Evidence not found"));
    if (record.isRetired()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "EVIDENCE_RETIRED", "Retired evidence cannot be used for a finding");
    }
    return record;
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String nextFindingCode(UUID inspectionId) {
    return "F-" + (findings.countIncludingRemovedByInspectionId(inspectionId) + 1);
  }

  private FindingResponse toFindingResponse(Finding finding) {
    return new FindingResponse(
        finding.getId(),
        finding.getInspectionId(),
        finding.getEvidenceId(),
        finding.getStatus(),
        finding.getFindingCode(),
        finding.getDefectLabel(),
        finding.getSeverity(),
        finding.getTechnicalNotes(),
        finding.getRecommendedAction(),
        finding.getBoundingBox(),
        finding.getModelName(),
        finding.getModelVersion(),
        finding.getModelProvider(),
        finding.getConfidence(),
        finding.getInspectorConfirmedAt(),
        finding.getForwardedToMaintenanceAt(),
        finding.getDeletedAt());
  }
}
