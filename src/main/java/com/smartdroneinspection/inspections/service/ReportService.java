package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.ReportSectionsRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportVersionResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportSectionsResponse;
import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.EvidenceQualityDecision;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.InspectionReportVersion;
import com.smartdroneinspection.inspections.domain.ReportSections;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.inspections.domain.enums.FindingDecision;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.events.ReportPublishedEvent;
import com.smartdroneinspection.inspections.repository.AiFindingCandidateRepository;
import com.smartdroneinspection.inspections.repository.EvidenceQualityDecisionRepository;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportVersionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.EvidenceQualitySummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.InspectionSummary;
import com.smartdroneinspection.inspections.spi.ReportRenderPort;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * MF3-07 to MF3-13: drafting, human verification, qualified review, publication and handoff.
 *
 * <p>The model drafts; humans decide. A draft is only generated from authorized inspection sources,
 * the author must verify what was generated, and a reviewer who is not the author approves. Nothing
 * publishes on a timer, and a published version is immutable — a correction creates a linked
 * version.
 */
@Service
public class ReportService {

  private static final String PROMPT_VERSION = "mf3-report-v2";

  /** Recorded as the version model when the Inspector authors the draft without automated help. */
  private static final String MANUAL_MODEL_NAME = "manual-authoring";

  private static final String PDF_MEDIA_TYPE = "application/pdf";

  private final InspectionRepository inspections;
  private final EvidenceQualityDecisionRepository qualityDecisions;
  private final EvidenceRepository evidence;
  private final VerifiedFindingRepository findings;
  private final AiFindingCandidateRepository candidates;
  private final FieldSessionRepository fieldSessions;
  private final InspectionReportRepository reports;
  private final InspectionReportVersionRepository versions;
  private final EvidenceService evidenceService;
  private final EvidenceQualityService qualityService;
  private final Optional<ReportDraftPort> draftPort;
  private final ReportRenderPort renderPort;
  private final Optional<EvidenceObjectStore> objectStore;
  private final UserAccess userAccess;
  private final ApplicationEventPublisher events;
  private final ObjectMapper objectMapper;

  public ReportService(
      InspectionRepository inspections,
      EvidenceRepository evidence,
      VerifiedFindingRepository findings,
      AiFindingCandidateRepository candidates,
      FieldSessionRepository fieldSessions,
      EvidenceQualityDecisionRepository qualityDecisions,
      InspectionReportRepository reports,
      InspectionReportVersionRepository versions,
      EvidenceService evidenceService,
      EvidenceQualityService qualityService,
      Optional<ReportDraftPort> draftPort,
      ReportRenderPort renderPort,
      Optional<EvidenceObjectStore> objectStore,
      UserAccess userAccess,
      ApplicationEventPublisher events,
      ObjectMapper objectMapper) {
    this.inspections = inspections;
    this.evidence = evidence;
    this.candidates = candidates;
    this.fieldSessions = fieldSessions;
    this.findings = findings;
    this.reports = reports;
    this.versions = versions;
    this.evidenceService = evidenceService;
    this.qualityService = qualityService;
    this.draftPort = draftPort;
    this.qualityDecisions = qualityDecisions;
    this.renderPort = renderPort;
    this.objectStore = objectStore;
    this.objectMapper = objectMapper;
    this.userAccess = userAccess;
    this.events = events;
  }

  /**
   * MF3-07/08: generate a draft from authorized sources, then let the author edit and verify it.
   */
  @Transactional
  public InspectionReportVersionResponse generateDraft(UUID actorId, UUID inspectionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);

    requireCurrentFindingsConfirmation(inspection);

    InspectionReport report =
        reports
            .findByInspectionId(inspectionId)
            .orElseGet(() -> reports.saveAndFlush(new InspectionReport(inspectionId, actorId)));
    if (!report.getAuthorUserId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the assigned Inspector authors this report");
    }

    EvidenceQualitySummary evidenceQuality =
        qualityDecisions
            .findFirstByInspectionIdOrderByDecidedAtDesc(inspectionId)
            .filter(EvidenceQualityDecision::isAccepted)
            .map(
                decision ->
                    new EvidenceQualitySummary(
                        decision.getDecision().name(),
                        decision.getShotListComparison(),
                        decision.getLimitationReason()))
            .orElse(null);
    List<Evidence> confirmedEvidence = evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    List<AiFindingCandidate> inspectionCandidates =
        candidates.findByInspectionIdOrderByCreatedAtDesc(inspectionId);
    List<FieldSession> recordedSessions =
        fieldSessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    List<VerifiedFinding> confirmedFindings = inspectorFindings(inspectionId);

    ReportDraftPort port =
        draftPort.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "REPORT_DRAFT_UNAVAILABLE",
                    "Automated drafting is not configured; author a structured draft manually"));

    ReportDraftPort.SectionDraft proposal;
    try {
      proposal =
          port.draftSections(
              new ReportDraftPort.DraftContext(
                  new InspectionSummary(
                      inspection.getObjective(),
                      inspection.getScope(),
                      inspection.getComponentScope(),
                      inspection.getAcceptanceCriteria(),
                      inspection.getPlannedStartAt() == null
                          ? null
                          : inspection.getPlannedStartAt().toString(),
                      inspection.getPlannedEndAt() == null
                          ? null
                          : inspection.getPlannedEndAt().toString(),
                      inspection.getDroneId() == null ? null : inspection.getDroneId().toString()),
                  List.of(),
                  confirmedFindings.stream()
                      .map(
                          finding ->
                              new ReportDraftPort.FindingSummary(
                                  finding.getDefectLabel(),
                                  finding.getSeverity().name(),
                                  finding.getComponent(),
                                  finding.getLocationDescription(),
                                  finding.getTechnicalNotes(),
                                  finding.getRecommendedAction()))
                      .toList(),
                  confirmedEvidence.stream()
                      .map(
                          item ->
                              new ReportDraftPort.EvidenceSummary(
                                  item.getFileName(),
                                  item.getContentType(),
                                  item.getCaptureTime() == null
                                      ? null
                                      : item.getCaptureTime().toString(),
                                  item.getLatitude() == null
                                      ? null
                                      : item.getLatitude().toPlainString(),
                                  item.getLongitude() == null
                                      ? null
                                      : item.getLongitude().toPlainString(),
                                  item.getSource().name(),
                                  item.getFieldSessionId() == null
                                      ? null
                                      : item.getFieldSessionId().toString()))
                      .toList(),
                  inspectionCandidates.stream()
                      .map(
                          candidate ->
                              new ReportDraftPort.CandidateSummary(
                                  candidate.getId().toString(),
                                  candidate.getEvidenceId().toString(),
                                  candidate.getModelProvider(),
                                  candidate.getModelName(),
                                  candidate.getModelVersion(),
                                  candidate.getPredictedLabel(),
                                  candidate.getConfidence() == null
                                      ? null
                                      : candidate.getConfidence().toPlainString(),
                                  candidate.getBoundingBox(),
                                  candidate.getStatus().name()))
                      .toList(),
                  recordedSessions.stream()
                      .map(
                          session ->
                              new ReportDraftPort.FieldSessionSummary(
                                  session.getId().toString(),
                                  session.getStatus(),
                                  session.getStartedAt() == null
                                      ? null
                                      : session.getStartedAt().toString(),
                                  session.getEndedAt() == null
                                      ? null
                                      : session.getEndedAt().toString(),
                                  session.getDroneId() == null
                                      ? null
                                      : session.getDroneId().toString()))
                      .toList(),
                  evidenceQuality));
    } catch (IOException ex) {
      // A failed generation permits a manual draft under the same review gates. No placeholder text
      // is written into a section: a section the author did not write and the model did not supply
      // stays absent, and reads as "not supplied" rather than as a confident blank.
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REPORT_DRAFT_UNAVAILABLE",
          "Automated drafting is temporarily unavailable");
    }

    String findingsSnapshot = currentFindingsSnapshotHash(inspectionId);
    int versionNo = report.nextVersionNumber();
    UUID sourceVersionId =
        versions
            .findFirstByInspectionReportIdAndStatus(report.getId(), ReportStatus.PUBLISHED)
            .map(InspectionReportVersion::getId)
            .orElse(null);
    InspectionReportVersion version =
        new InspectionReportVersion(
            report.getId(),
            versionNo,
            sourceVersionId,
            actorId,
            buildContentSnapshot(inspection, confirmedEvidence, confirmedFindings, proposal, port),
            null,
            port.modelName(),
            PROMPT_VERSION,
            evidenceSnapshotHash(confirmedEvidence),
            findingsSnapshot);
    report.recordNewVersion(versionNo);
    report.setStatus(ReportStatus.DRAFT);
    reports.saveAndFlush(report);
    return toResponse(versions.saveAndFlush(version));
  }

  /**
   * MF3-08 manual authoring. Report 3 requires that a failed or unconfigured drafting service still
   * lets the author complete a structured draft, so this creates the same version record under the
   * same review gates and records that no automated drafting contributed to it.
   */
  @Transactional
  public InspectionReportVersionResponse authorManualDraft(
      UUID actorId, UUID inspectionId, String narrative, String omissionDisclosure) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    requireCurrentFindingsConfirmation(inspection);
    InspectionReport report = requireAuthoredReport(actorId, inspectionId);

    List<Evidence> confirmedEvidence = evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    List<VerifiedFinding> confirmedFindings = inspectorFindings(inspectionId);
    String findingsSnapshot = currentFindingsSnapshotHash(inspectionId);

    int versionNo = report.nextVersionNumber();
    UUID sourceVersionId =
        versions
            .findFirstByInspectionReportIdAndStatus(report.getId(), ReportStatus.PUBLISHED)
            .map(InspectionReportVersion::getId)
            .orElse(null);
    InspectionReportVersion version =
        new InspectionReportVersion(
            report.getId(),
            versionNo,
            sourceVersionId,
            actorId,
            buildManualContentSnapshot(
                inspection, confirmedEvidence, confirmedFindings, narrative, omissionDisclosure),
            null,
            MANUAL_MODEL_NAME,
            PROMPT_VERSION,
            evidenceSnapshotHash(confirmedEvidence),
            findingsSnapshot);
    report.recordNewVersion(versionNo);
    report.setStatus(ReportStatus.DRAFT);
    reports.saveAndFlush(report);
    return toResponse(versions.saveAndFlush(version));
  }

  private InspectionReport requireAuthoredReport(UUID actorId, UUID inspectionId) {
    InspectionReport report =
        reports
            .findByInspectionId(inspectionId)
            .orElseGet(() -> reports.saveAndFlush(new InspectionReport(inspectionId, actorId)));
    if (!report.getAuthorUserId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the assigned Inspector authors this report");
    }
    return report;
  }

  private List<VerifiedFinding> inspectorFindings(UUID inspectionId) {
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .filter(finding -> finding.getDecision() == null || finding.isOfficial())
        .toList();
  }

  /**
   * MF3-06/09/10: the findings the delivered document lists under Confirmed findings.
   *
   * <p>An Inspector proposal belongs here. MF3-06 confirmation is already a human decision and the
   * MF3-09 reviewer verdict refines a proposed finding at the submitted report; it does not decide
   * whether the defect appears in the draft at all. Publication cannot reach this state unresolved,
   * because approval and publication both refuse while any proposal is undecided, so a delivered
   * document never carries a pending verdict.
   *
   * <p>Only a rejected finding is excluded: MF3-10 keeps it auditable but not official.
   */
  private List<VerifiedFinding> reportableFindings(UUID inspectionId) {
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .filter(finding -> finding.getDecision() != FindingDecision.REJECTED)
        .toList();
  }

  /**
   * MF3-08: the author reads and edits the entire draft; verify identifiers, equipment, coverage,
   * findings, uncertainty and recommendations against sources; submit the reviewed version.
   */
  @Transactional
  public InspectionReportVersionResponse saveSections(
      UUID actorId, UUID inspectionId, UUID versionId, ReportSectionsRequest request) {
    InspectionReport report = requireAuthoredReport(actorId, inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);

    // Authoring is the author's work. A reviewer who is not the author may read a draft but cannot
    // rewrite it, and the author cannot write onto a version that has left an editable state.
    if (!version.getAuthorUserId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the report author may edit this draft");
    }

    requireCurrentFindingsConfirmation(
        evidenceService.requireAssignedInspection(actorId, inspectionId));
    requireFreshFindingsSnapshot(inspectionId, version);
    ReportSections sections =
        new ReportSections(
            request.executiveSummary(),
            request.purposeScopeCriteria(),
            request.methodEquipmentConditions(),
            request.conclusionsRecommendations(),
            request.overallCondition(),
            request.omissionDisclosure());
    String updated = sections.applyTo(version.getContentSnapshot(), objectMapper);
    try {
      version.editContent(updated);
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }
    return toResponse(versions.saveAndFlush(version));
  }

  /** MF3-08: the author verifies the draft against its sources. */
  @Transactional
  public InspectionReportVersionResponse verifyVersion(
      UUID actorId, UUID inspectionId, UUID versionId) {
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    requireFreshEvidenceSnapshot(inspectionId, version);
    requireFreshFindingsSnapshot(inspectionId, version);
    requireCurrentFindingsConfirmation(
        evidenceService.requireAssignedInspection(actorId, inspectionId));
    try {
      version.verifyAsAuthor(actorId);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }
    report.setStatus(ReportStatus.AUTHOR_VERIFIED);
    reports.saveAndFlush(report);
    return toResponse(versions.saveAndFlush(version));
  }

  /** MF3-08: submit the verified version for review. */
  @Transactional
  public InspectionReportVersionResponse submitVersion(
      UUID actorId, UUID inspectionId, UUID versionId) {
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    requireFreshEvidenceSnapshot(inspectionId, version);
    requireFreshFindingsSnapshot(inspectionId, version);
    requireCurrentFindingsConfirmation(
        evidenceService.requireAssignedInspection(actorId, inspectionId));
    try {
      version.submit();
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }
    report.setStatus(ReportStatus.SUBMITTED);
    reports.saveAndFlush(report);
    return toResponse(versions.saveAndFlush(version));
  }

  /**
   * MF3-04: evidence added after the Inspector's confirmation makes a dependent draft stale. The
   * stored snapshot hash is compared against the current evidence set, so a version drafted over
   * older captures cannot be verified or submitted — the author drafts a new version instead.
   */
  private void requireCurrentFindingsConfirmation(Inspection inspection) {
    if (!qualityService.isEligibleForAnalysis(inspection.getId())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_STATE_CONFLICT",
          "A draft requires an accepted evidence quality decision");
    }
    String current = currentFindingsSnapshotHash(inspection.getId());
    if (!inspection.hasCurrentFindingsConfirmation(current)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_NOT_CONFIRMED",
          "Confirm the current findings before authoring a report");
    }
  }

  private String currentFindingsSnapshotHash(UUID inspectionId) {
    return FindingsSnapshot.hash(
        evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId),
        candidates.findByInspectionIdOrderByCreatedAtDesc(inspectionId),
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId));
  }

  private void requireFreshFindingsSnapshot(UUID inspectionId, InspectionReportVersion version) {
    String current = currentFindingsSnapshotHash(inspectionId);
    if (!current.equals(version.getFindingsSnapshotHash())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_SNAPSHOT_STALE",
          "Findings changed after this draft was created; author a new draft version");
    }
  }

  private boolean hasUndecidedFindings(UUID inspectionId) {
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .anyMatch(finding -> !finding.hasReviewerDecision());
  }

  private void requireFreshEvidenceSnapshot(UUID inspectionId, InspectionReportVersion version) {
    String current =
        evidenceSnapshotHash(evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId));
    if (!current.equals(version.getEvidenceSnapshotHash())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_SNAPSHOT_STALE",
          "Evidence changed after this draft was created; author a new draft version");
    }
  }

  /**
   * MF3-09: the qualified reviewer decides. Only an in-organization ORG_ADMIN who is not the author
   * may review, and returning a draft requires a reason.
   */
  @Transactional
  public InspectionReportVersionResponse reviewVersion(
      UUID actorId, UUID inspectionId, UUID versionId, boolean approve, String reason) {
    UserAccess.ActiveUser reviewer = requireQualifiedReviewer(actorId, inspectionId);
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    if (!approve) {
      try {
        version.reviewBy(reviewer.id(), false, reason);
      } catch (IllegalStateException | IllegalArgumentException ex) {
        throw new BusinessException(
            HttpStatus.CONFLICT, "REPORT_REVIEWER_INVALID", ex.getMessage());
      }
      report.setStatus(ReportStatus.RETURNED);
      reports.saveAndFlush(report);
      return toResponse(versions.saveAndFlush(version));
    }
    if (hasUndecidedFindings(inspectionId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_REVIEW_INCOMPLETE",
          "Decide every proposed finding before approving the report");
    }
    try {
      version.reviewBy(reviewer.id(), approve, reason);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_REVIEWER_INVALID", ex.getMessage());
    }
    report.setStatus(approve ? ReportStatus.APPROVED : ReportStatus.RETURNED);
    reports.saveAndFlush(report);
    return toResponse(versions.saveAndFlush(version));
  }

  /** MF3-11/12/13: publish, mark corrective items, and hand the approved findings to MF4. */
  @Transactional
  public ReportPublishedResponse publish(UUID actorId, UUID inspectionId, UUID versionId) {
    UserAccess.ActiveUser publisher = requireQualifiedReviewer(actorId, inspectionId);
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    if (hasUndecidedFindings(inspectionId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_REVIEW_INCOMPLETE",
          "Decide every proposed finding before publishing the report");
    }
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    requireFreshEvidenceSnapshot(inspectionId, version);
    requireFreshFindingsSnapshot(inspectionId, version);
    try {
      version.publish(publisher.id());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }

    // MF3-11: the PDF is the version's deliverable, so it is rendered and stored before the version
    // can be marked published. A failure here rolls the publish back rather than leaving a
    // published
    // version with no artefact.
    storePublishedArtefact(inspectionId, version);
    versions.saveAndFlush(version);

    // MF3-11: a new published version supersedes any previously published version of this report.
    List<InspectionReportVersion> previouslyPublished =
        versions.findByInspectionReportIdAndStatus(report.getId(), ReportStatus.PUBLISHED);
    for (InspectionReportVersion prev : previouslyPublished) {
      if (!prev.getId().equals(version.getId())) {
        prev.supersede();
        versions.save(prev);
      }
    }

    report.setStatus(ReportStatus.PUBLISHED);
    reports.saveAndFlush(report);

    // MF3-11 ends here: the PDF is stored, checksummed and the version is immutable. Corrective
    // action (MF3-12) and the MF4 handoff (MF3-13) are separate later acts, because a published
    // report is a record of observed condition and must not be reopened to carry maintenance
    // planning data.
    inspection.markReportPublished();
    inspections.saveAndFlush(inspection);

    // MF3-12: any corrective-action outcome recorded before this publication belongs to an earlier
    // version or to the pre-cutover review decision. Clearing it keeps the new version from being
    // presented with a disposition nobody recorded after it, and the reviewer records a fresh one.
    List<VerifiedFinding> staleOutcomes =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(VerifiedFinding::isOfficial)
            .filter(VerifiedFinding::isCorrectiveActionReviewed)
            .toList();
    staleOutcomes.forEach(VerifiedFinding::clearCorrectiveActionOutcome);
    if (!staleOutcomes.isEmpty()) {
      findings.saveAll(staleOutcomes);
    }

    return new ReportPublishedResponse(
        version.getId(),
        version.getVersionNo(),
        version.getPublishedAt(),
        List.of(),
        inspection.getStatus());
  }

  /**
   * MF3-13: hands the published report's corrective items to MF4 after every confirmed finding has
   * an explicit MF3-12 corrective-action outcome.
   *
   * <p>MF4 work-order creation is not implemented; this records the handoff and inspection status,
   * but does not create a work order.
   */
  @Transactional
  public ReportPublishedResponse completeCorrectiveActionHandoff(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser reviewer = requireQualifiedReviewer(actorId, inspectionId);
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    if (inspection.getCorrectiveHandoffAt() != null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CORRECTIVE_HANDOFF_ALREADY_COMPLETED",
          "Corrective handoff has already been completed");
    }
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version =
        versions
            .findFirstByInspectionReportIdAndStatus(report.getId(), ReportStatus.PUBLISHED)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "REPORT_STATE_CONFLICT",
                        "Publish the report before handing corrective items to maintenance"));

    List<VerifiedFinding> official =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(VerifiedFinding::isOfficial)
            .toList();
    if (official.stream().anyMatch(finding -> !finding.isCorrectiveActionReviewed())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CORRECTIVE_ACTION_REVIEW_INCOMPLETE",
          "Record corrective action or an explicit no-action outcome for every confirmed finding");
    }
    List<UUID> repairRequired =
        official.stream()
            .filter(VerifiedFinding::isCorrectiveActionRequired)
            .map(VerifiedFinding::getId)
            .toList();
    if (official.isEmpty()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "NO_CORRECTIVE_WORK_PENDING",
          "Record an explicit no-corrective-work rationale for a scope without confirmed findings");
    }
    try {
      inspection.recordCorrectiveHandoff(reviewer.id(), null);
      if (repairRequired.isEmpty()) {
        inspection.complete();
      } else {
        inspection.markRepairPending();
      }
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", ex.getMessage());
    }
    inspections.saveAndFlush(inspection);

    events.publishEvent(
        new ReportPublishedEvent(
            report.getId(),
            version.getId(),
            inspectionId,
            inspection.getOrganizationId(),
            inspection.getAssetId(),
            reviewer.id(),
            repairRequired,
            version.getPublishedAt()));
    return new ReportPublishedResponse(
        version.getId(),
        version.getVersionNo(),
        version.getPublishedAt(),
        repairRequired,
        inspection.getStatus());
  }

  /**
   * MF3-12: records the reviewer’s explicit no-repair outcome after report publication.
   *
   * <p>This route is for a scope that has no official finding requiring corrective work. If there
   * are any official findings, the reviewer must record the outcome on each one instead.
   */
  @Transactional
  public ReportPublishedResponse declareNoCorrectiveWork(
      UUID actorId, UUID inspectionId, String rationale) {
    UserAccess.ActiveUser reviewer = requireQualifiedReviewer(actorId, inspectionId);
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    if (inspection.getCorrectiveHandoffAt() != null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CORRECTIVE_HANDOFF_ALREADY_COMPLETED",
          "Corrective handoff has already been completed");
    }
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version =
        versions
            .findFirstByInspectionReportIdAndStatus(report.getId(), ReportStatus.PUBLISHED)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "REPORT_STATE_CONFLICT",
                        "Publish the report before declaring no corrective work"));
    boolean hasOfficialFindings =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .anyMatch(VerifiedFinding::isOfficial);
    if (!inspection.hasFindingsConfirmation()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_NOT_CONFIRMED",
          "The Inspector must explicitly confirm the observed findings set first");
    }
    if (hasOfficialFindings) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "CORRECTIVE_ACTION_REVIEW_REQUIRED",
          "Record a corrective-action outcome for each confirmed finding");
    }
    if (rationale == null || rationale.isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_REPAIR_RATIONALE_REQUIRED",
          "Record why no corrective work is required within the observed scope");
    }
    try {
      inspection.recordCorrectiveHandoff(reviewer.id(), rationale);
      inspection.complete();
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", ex.getMessage());
    }
    inspections.saveAndFlush(inspection);
    events.publishEvent(
        new ReportPublishedEvent(
            report.getId(),
            version.getId(),
            inspectionId,
            inspection.getOrganizationId(),
            inspection.getAssetId(),
            reviewer.id(),
            List.of(),
            version.getPublishedAt()));
    return new ReportPublishedResponse(
        version.getId(),
        version.getVersionNo(),
        version.getPublishedAt(),
        List.of(),
        inspection.getStatus());
  }

  /**
   * MF3-11: renders the deliverable, stores it, and records its checksum on the version.
   *
   * <p>The object key is derived from the version id, so publishing a later version never
   * overwrites the bytes stored for an already-published one. The checksum is taken from the bytes
   * that were stored, so it describes what a later reader would actually download.
   */
  private void storePublishedArtefact(UUID inspectionId, InspectionReportVersion version) {
    byte[] pdf = renderArtefact(inspectionId, version, false);
    String objectKey = reportObjectKey(version.getId());
    try (InputStream input = new ByteArrayInputStream(pdf)) {
      requireObjectStore().put(objectKey, PDF_MEDIA_TYPE, pdf.length, input);
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REPORT_RENDER_UNAVAILABLE",
          "The report document could not be stored; publication did not complete");
    }
    version.recordArtefact(objectKey, sha256Hex(pdf));
  }

  /**
   * MF3-08: the author may render a preview of a draft to check the delivered layout before
   * submitting it.
   *
   * <p>A preview is the author's working view. It is not stored against the version, is not
   * checksummed and carries no approval block, so it cannot be mistaken for the published artefact.
   */
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> preview(UUID actorId, UUID inspectionId, UUID versionId) {
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    if (!version.getAuthorUserId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the report author may preview this draft");
    }
    return pdfResponse(
        renderArtefact(inspectionId, version, true),
        "inline",
        documentReference(version) + "-preview.pdf");
  }

  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> downloadArtefact(UUID actorId, UUID inspectionId, UUID versionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    String objectKey = version.getRenderedObjectKey();
    if (objectKey == null || objectKey.isBlank()) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND,
          "REPORT_ARTEFACT_NOT_FOUND",
          "This report version has no stored document");
    }
    byte[] content;
    try (InputStream input = requireObjectStore().open(objectKey)) {
      content = input.readAllBytes();
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND,
          "REPORT_ARTEFACT_NOT_FOUND",
          "This report version has no stored document");
    }
    return pdfResponse(content, "attachment", documentReference(version) + ".pdf");
  }

  private byte[] renderArtefact(
      UUID inspectionId, InspectionReportVersion version, boolean preview) {
    try {
      return renderPort.render(buildRenderContext(inspectionId, version, preview));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REPORT_RENDER_UNAVAILABLE",
          preview
              ? "The report preview could not be produced"
              : "The report document could not be produced; publication did not complete");
    }
  }

  private static ResponseEntity<byte[]> pdfResponse(
      byte[] content, String disposition, String fileName) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .contentLength(content.length)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.builder(disposition)
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(content);
  }

  private static String documentReference(InspectionReportVersion version) {
    return "SDI-RPT-"
        + version.getId().toString().substring(0, 8).toUpperCase()
        + "-V"
        + version.getVersionNo();
  }

  static String reportObjectKey(UUID versionId) {
    return "reports/" + versionId + ".pdf";
  }

  private static String sha256Hex(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * MF3-11/03: assembles everything the delivered document needs.
   *
   * <p>Confirmed findings and evidence are read from their own records at render time rather than
   * from the stored body, so the document cannot disagree with the finding decisions. Evidence
   * bytes are loaded here so the PDF embeds the stored original rather than a reference to it.
   */
  private ReportRenderPort.RenderContext buildRenderContext(
      UUID inspectionId, InspectionReportVersion version, boolean preview) {
    Inspection inspection = inspections.findById(inspectionId).orElseThrow();

    ReportSections sections = ReportSections.read(version.getContentSnapshot(), objectMapper);

    List<ReportRenderPort.FindingView> findingViews = new ArrayList<>();
    Map<UUID, List<String>> componentLocationByEvidence = new LinkedHashMap<>();
    Map<UUID, Evidence> evidenceById = new LinkedHashMap<>();
    for (Evidence item : evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId)) {
      evidenceById.put(item.getId(), item);
    }
    List<VerifiedFinding> confirmed = reportableFindings(inspectionId);
    for (VerifiedFinding finding : confirmed) {
      List<String> evidenceRefs = new ArrayList<>();
      if (finding.getEvidenceId() != null) {
        evidenceRefs.add(finding.getEvidenceId().toString());
        Evidence evItem = evidenceById.get(finding.getEvidenceId());
        if (evItem != null) {
          evidenceRefs.add(evItem.getFileName());
        }
        String componentLocation =
            componentLocation(finding.getComponent(), finding.getLocationDescription());
        if (componentLocation != null) {
          List<String> values =
              componentLocationByEvidence.computeIfAbsent(
                  finding.getEvidenceId(), ignored -> new ArrayList<>());
          if (!values.contains(componentLocation)) {
            values.add(componentLocation);
          }
        }
      }
      findingViews.add(
          new ReportRenderPort.FindingView(
              finding.getId().toString(),
              finding.getComponent(),
              finding.getDefectLabel(),
              finding.getSeverity().name(),
              finding.getLocationDescription(),
              finding.getObservedCondition(),
              finding.getDescription(),
              finding.getTechnicalNotes(),
              finding.getRecommendedAction(),
              // A finding may still await its MF3-09 reviewer verdict. It is reported as awaiting
              // one
              // rather than as decided, so the document never implies a decision nobody recorded.
              finding.getDecision() == null
                  ? "Awaiting reviewer decision"
                  : finding.getDecision().name(),
              displayName(finding.getDecidedByUserId()),
              finding.getDecidedAt() == null ? null : finding.getDecidedAt().toString(),
              finding.getMeasurement(),
              finding.isRepairRequired() ? "Yes" : "No",
              evidenceRefs));
    }

    List<ReportRenderPort.EvidenceView> evidenceViews = new ArrayList<>();
    for (Evidence item : evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId)) {
      byte[] content = loadEvidenceBytes(item);
      evidenceViews.add(
          new ReportRenderPort.EvidenceView(
              item.getId().toString(),
              item.getFileName(),
              item.getContentType(),
              item.getChecksumSha256(),
              item.getCaptureTime() == null ? null : item.getCaptureTime().toString(),
              componentLocationByEvidence.containsKey(item.getId())
                  ? String.join("; ", componentLocationByEvidence.get(item.getId()))
                  : null,
              content));
    }

    return new ReportRenderPort.RenderContext(
        documentReference(version),
        version.getVersionNo(),
        version.getStatus().name(),
        inspection.getObjective(),
        inspection.getAssetId().toString(),
        inspection.getOrganizationId().toString(),
        displayName(version.getAuthorUserId()),
        displayName(version.getReviewerUserId()),
        version.getAuthorVerifiedAt() == null ? null : version.getAuthorVerifiedAt().toString(),
        version.getReviewedAt() == null ? null : version.getReviewedAt().toString(),
        version.getPublishedAt() == null ? null : version.getPublishedAt().toString(),
        version.getEvidenceSnapshotHash(),
        new ReportRenderPort.Sections(
            sections.executiveSummary(),
            sections.purposeScopeCriteria(),
            sections.methodEquipmentConditions(),
            sections.conclusionsRecommendations(),
            sections.overallCondition() == null ? null : sections.overallCondition().name(),
            sections.omissionDisclosure()),
        findingViews,
        evidenceViews);
  }

  /**
   * Loads the stored evidence bytes so the document embeds the original file.
   *
   * <p>A file that cannot be read is reported without its image rather than failing the whole
   * render: one unreadable upload should not cost the reader the report.
   */
  private EvidenceObjectStore requireObjectStore() {
    return objectStore.orElseThrow(
        () ->
            new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "REPORT_ARTEFACT_UNAVAILABLE",
                "Report document storage is not configured"));
  }

  private byte[] loadEvidenceBytes(Evidence item) {
    try (InputStream input = requireObjectStore().open(item.getObjectKey())) {
      return input.readAllBytes();
    } catch (IOException ex) {
      return new byte[0];
    }
  }

  /**
   * Resolves a user's name for the delivered document.
   *
   * <p>The users module is reached only through its {@code UserAccess} boundary, so this module
   * does not depend on a users-internal repository. An unresolvable identity falls back to the
   * identifier rather than leaving the approval block blank: the record must name who acted even
   * when the profile is gone.
   */
  private String displayName(UUID userId) {
    if (userId == null) {
      return null;
    }
    return userAccess
        .findActiveUser(userId)
        .map(UserAccess.ActiveUser::fullName)
        .filter(name -> name != null && !name.isBlank())
        .orElse(userId.toString());
  }

  @Transactional(readOnly = true)
  public List<InspectionReportVersionResponse> listVersions(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    // An inspection whose evidence is not yet accepted has no report at all. That is a normal
    // empty state for the reviewer, not a missing resource, so it is reported as an empty list.
    return reports
        .findByInspectionId(inspectionId)
        .map(
            report ->
                versions.findByInspectionReportIdOrderByVersionNoDesc(report.getId()).stream()
                    .map(this::toResponse)
                    .toList())
        .orElseGet(List::of);
  }

  @Transactional(readOnly = true)
  public InspectionReportVersionResponse getVersion(
      UUID actorId, UUID inspectionId, UUID versionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    InspectionReport report = requireReportForInspection(inspectionId);
    return toResponse(requireVersion(report.getId(), versionId));
  }

  private UserAccess.ActiveUser requireQualifiedReviewer(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
    if (actor.organizationId() == null || !actor.hasRole("ORG_ADMIN")) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "REPORT_SCOPE_DENIED", "Only a qualified ORG_ADMIN may review");
    }
    Inspection inspection =
        inspections
            .findByIdAndOrganizationId(inspectionId, actor.organizationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Inspection report not found"));
    // Separation of duties: no report is self-reviewed.
    if (inspection.getInspectorId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_REVIEWER_INVALID",
          "The report author cannot review their own report");
    }
    return actor;
  }

  private InspectionReport requireReportForInspection(UUID inspectionId) {
    return reports
        .findByInspectionId(inspectionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Inspection report not found"));
  }

  private InspectionReportVersion requireVersion(UUID reportId, UUID versionId) {
    return versions
        .findById(versionId)
        .filter(version -> version.getInspectionReportId().equals(reportId))
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report version not found"));
  }

  /**
   * Stores the proposed section values alongside the version's provenance.
   *
   * <p>Findings are deliberately not copied in: the Confirmed findings section is rendered from the
   * reviewed finding records, so a copy here could disagree with them.
   */
  private static String buildContentSnapshot(
      Inspection inspection,
      List<Evidence> confirmedEvidence,
      List<VerifiedFinding> confirmedFindings,
      ReportDraftPort.SectionDraft proposal,
      ReportDraftPort port) {
    StringBuilder snapshot = new StringBuilder();
    snapshot.append("{\"model\":").append(quote(port.modelName()));
    snapshot.append(",\"assetId\":").append(quote(inspection.getAssetId().toString()));
    snapshot.append(",\"inspectionId\":").append(quote(inspection.getId().toString()));
    snapshot.append(",\"objective\":").append(quote(inspection.getObjective()));
    snapshot.append(",\"evidenceCount\":").append(confirmedEvidence.size());
    snapshot.append(",\"findingsCount\":").append(confirmedFindings.size());
    appendSection(snapshot, "executiveSummary", proposal.executiveSummary());
    appendSection(snapshot, "purposeScopeCriteria", proposal.purposeScopeCriteria());
    appendSection(snapshot, "methodEquipmentConditions", proposal.methodEquipmentConditions());
    appendSection(snapshot, "conclusionsRecommendations", proposal.conclusionsRecommendations());
    appendSection(snapshot, "overallCondition", proposal.overallCondition());
    appendSection(snapshot, "omissionDisclosure", proposal.omissionDisclosure());
    snapshot.append(",\"narrative\":").append(quote(proposal.narrative()));
    snapshot.append("}");
    return snapshot.toString();
  }

  /** Writes a section only when the model supplied one, so an absent section stays absent. */
  private static void appendSection(StringBuilder snapshot, String key, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    snapshot.append(",\"").append(key).append("\":").append(quote(value));
  }

  /**
   * The manual variant records why no automated drafting contributed, so a reader can see that the
   * narrative is the author's own and which analysis was omitted.
   */
  private static String buildManualContentSnapshot(
      Inspection inspection,
      List<Evidence> confirmedEvidence,
      List<VerifiedFinding> confirmedFindings,
      String narrative,
      String omissionDisclosure) {
    return "{\"model\":"
        + quote(MANUAL_MODEL_NAME)
        + ",\"assetId\":"
        + quote(inspection.getAssetId().toString())
        + ",\"inspectionId\":"
        + quote(inspection.getId().toString())
        + ",\"objective\":"
        + quote(inspection.getObjective())
        + ",\"evidenceCount\":"
        + confirmedEvidence.size()
        + ",\"findingsCount\":"
        + confirmedFindings.size()
        + ",\"narrative\":"
        + quote(narrative)
        + ",\"omissionDisclosure\":"
        + quote(omissionDisclosure)
        + "}";
  }

  private static String evidenceSnapshotHash(List<Evidence> confirmedEvidence) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (Evidence item : confirmedEvidence) {
        digest.update(item.getChecksumSha256().getBytes());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static String quote(String value) {
    if (value == null) {
      return "null";
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
  }

  private static String componentLocation(String component, String location) {
    String cleanComponent = component == null || component.isBlank() ? null : component.trim();
    String cleanLocation = location == null || location.isBlank() ? null : location.trim();
    if (cleanComponent == null) {
      return cleanLocation;
    }
    if (cleanLocation == null) {
      return cleanComponent;
    }
    if (cleanComponent.equals(cleanLocation)) {
      return cleanComponent;
    }
    return cleanComponent + " — " + cleanLocation;
  }

  private InspectionReportVersionResponse toResponse(InspectionReportVersion version) {
    JsonNode content = readContentSnapshot(version.getContentSnapshot());
    return new InspectionReportVersionResponse(
        version.getId(),
        version.getInspectionReportId(),
        version.getVersionNo(),
        version.getStatus(),
        version.getAuthorUserId(),
        version.getAuthorVerifiedAt(),
        version.getReviewerUserId(),
        version.getReviewedAt(),
        version.getReviewReason(),
        version.getLlmModel(),
        version.getPromptVersion(),
        version.getGeneratedAt(),
        version.getEvidenceSnapshotHash(),
        version.getFindingsSnapshotHash(),
        version.getPublishedAt(),
        version.getCreatedAt(),
        textOrNull(content, "narrative"),
        textOrNull(content, "omissionDisclosure"),
        version.getContentSnapshot(),
        ReportSectionsResponse.from(
            ReportSections.read(version.getContentSnapshot(), objectMapper)));
  }

  /**
   * Reads the stored document body so the response carries what the author wrote.
   *
   * <p>A snapshot that cannot be parsed yields no body rather than failing the read. The version's
   * gates, hashes and provenance are still authoritative, and refusing to list them would turn a
   * malformed historical row into an outage.
   */
  private JsonNode readContentSnapshot(String contentSnapshot) {
    if (contentSnapshot == null || contentSnapshot.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(contentSnapshot);
    } catch (JacksonException exception) {
      return null;
    }
  }

  private static String textOrNull(JsonNode content, String field) {
    if (content == null) {
      return null;
    }
    JsonNode value = content.get(field);
    return value == null || value.isNull() ? null : value.asString();
  }
}
