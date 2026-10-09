package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.InspectionReportVersionResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.InspectionReportVersion;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.events.ReportPublishedEvent;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportVersionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  private static final String PROMPT_VERSION = "mf3-report-v1";

  /** Recorded as the version model when the Inspector authors the draft without automated help. */
  private static final String MANUAL_MODEL_NAME = "manual-authoring";

  private final InspectionRepository inspections;
  private final EvidenceRepository evidence;
  private final VerifiedFindingRepository findings;
  private final InspectionReportRepository reports;
  private final InspectionReportVersionRepository versions;
  private final EvidenceService evidenceService;
  private final EvidenceQualityService qualityService;
  private final Optional<ReportDraftPort> draftPort;
  private final UserAccess userAccess;
  private final ApplicationEventPublisher events;

  public ReportService(
      InspectionRepository inspections,
      EvidenceRepository evidence,
      VerifiedFindingRepository findings,
      InspectionReportRepository reports,
      InspectionReportVersionRepository versions,
      EvidenceService evidenceService,
      EvidenceQualityService qualityService,
      Optional<ReportDraftPort> draftPort,
      UserAccess userAccess,
      ApplicationEventPublisher events) {
    this.inspections = inspections;
    this.evidence = evidence;
    this.findings = findings;
    this.reports = reports;
    this.versions = versions;
    this.evidenceService = evidenceService;
    this.qualityService = qualityService;
    this.draftPort = draftPort;
    this.userAccess = userAccess;
    this.events = events;
  }

  /**
   * MF3-07/08: generate a draft from authorized sources, then let the author edit and verify it.
   */
  @Transactional
  public InspectionReportVersionResponse generateDraft(UUID actorId, UUID inspectionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);

    if (!qualityService.isEligibleForAnalysis(inspectionId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_STATE_CONFLICT",
          "A draft requires an accepted evidence quality decision");
    }

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

    List<Evidence> confirmedEvidence = evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    List<VerifiedFinding> confirmedFindings =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(VerifiedFinding::isOfficial)
            .toList();

    ReportDraftPort port =
        draftPort.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "REPORT_DRAFT_UNAVAILABLE",
                    "Automated drafting is not configured; author a structured draft manually"));

    String narrative;
    try {
      narrative =
          port.generateDraft(
              new ReportDraftPort.DraftContext(
                  List.of(),
                  confirmedFindings.stream()
                      .map(
                          finding ->
                              new ReportDraftPort.FindingSummary(
                                  finding.getDefectLabel(),
                                  finding.getSeverity().name(),
                                  finding.getTechnicalNotes()))
                      .toList(),
                  confirmedEvidence.stream()
                      .map(
                          item ->
                              new ReportDraftPort.EvidenceSummary(
                                  item.getFileName(), item.getContentType()))
                      .toList()));
    } catch (IOException ex) {
      // A failed generation permits a manual draft under the same review gates.
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REPORT_DRAFT_UNAVAILABLE",
          "Automated drafting is temporarily unavailable");
    }

    int versionNo = report.nextVersionNumber();
    InspectionReportVersion version =
        new InspectionReportVersion(
            report.getId(),
            versionNo,
            null,
            actorId,
            buildContentSnapshot(inspection, confirmedEvidence, confirmedFindings, narrative, port),
            null,
            port.modelName(),
            PROMPT_VERSION,
            evidenceSnapshotHash(confirmedEvidence));
    report.recordNewVersion(versionNo);
    report.setStatus(ReportStatus.DRAFT);
    reports.saveAndFlush(report);
    beginReportDrafting(inspection);
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
    if (!qualityService.isEligibleForAnalysis(inspectionId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_STATE_CONFLICT",
          "A draft requires an accepted evidence quality decision");
    }
    InspectionReport report = requireAuthoredReport(actorId, inspectionId);

    List<Evidence> confirmedEvidence = evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    List<VerifiedFinding> confirmedFindings = officialFindings(inspectionId);

    int versionNo = report.nextVersionNumber();
    InspectionReportVersion version =
        new InspectionReportVersion(
            report.getId(),
            versionNo,
            null,
            actorId,
            buildManualContentSnapshot(
                inspection, confirmedEvidence, confirmedFindings, narrative, omissionDisclosure),
            null,
            MANUAL_MODEL_NAME,
            PROMPT_VERSION,
            evidenceSnapshotHash(confirmedEvidence));
    report.recordNewVersion(versionNo);
    report.setStatus(ReportStatus.DRAFT);
    reports.saveAndFlush(report);
    beginReportDrafting(inspection);
    return toResponse(versions.saveAndFlush(version));
  }

  /**
   * The first draft moves the inspection out of field work. Publication later requires this state,
   * so leaving it unmoved would make every publish fail.
   */
  private void beginReportDrafting(Inspection inspection) {
    if (inspection.getStatus() == InspectionStatus.FIELD_COMPLETED) {
      inspection.beginReportDraft();
    }
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

  private List<VerifiedFinding> officialFindings(UUID inspectionId) {
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .filter(VerifiedFinding::isOfficial)
        .toList();
  }

  /** MF3-08: the author verifies the draft against its sources. */
  @Transactional
  public InspectionReportVersionResponse verifyVersion(
      UUID actorId, UUID inspectionId, UUID versionId) {
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
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
   * MF3-09: the qualified reviewer decides. Only an in-organization ORG_ADMIN who is not the author
   * may review, and returning a draft requires a reason.
   */
  @Transactional
  public InspectionReportVersionResponse reviewVersion(
      UUID actorId, UUID inspectionId, UUID versionId, boolean approve, String reason) {
    UserAccess.ActiveUser reviewer = requireQualifiedReviewer(actorId, inspectionId);
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
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
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    try {
      version.publish(publisher.id());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }
    versions.saveAndFlush(version);
    report.setStatus(ReportStatus.PUBLISHED);
    reports.saveAndFlush(report);

    List<UUID> repairRequired =
        findings.findRepairRequired(inspectionId).stream().map(VerifiedFinding::getId).toList();
    inspection.markReportPublished();
    if (repairRequired.isEmpty()) {
      // A no-repair outcome closes with its limitations preserved, never an empty work order.
      inspection.complete();
    } else {
      inspection.markRepairPending();
    }
    inspections.saveAndFlush(inspection);

    events.publishEvent(
        new ReportPublishedEvent(
            report.getId(),
            version.getId(),
            inspectionId,
            inspection.getOrganizationId(),
            inspection.getAssetId(),
            publisher.id(),
            repairRequired,
            version.getPublishedAt()));
    return new ReportPublishedResponse(
        version.getId(),
        version.getVersionNo(),
        version.getPublishedAt(),
        repairRequired,
        inspection.getStatus());
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

  private static String buildContentSnapshot(
      Inspection inspection,
      List<Evidence> confirmedEvidence,
      List<VerifiedFinding> confirmedFindings,
      String narrative,
      ReportDraftPort port) {
    return "{\"model\":"
        + quote(port.modelName())
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
        + "}";
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

  private InspectionReportVersionResponse toResponse(InspectionReportVersion version) {
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
        version.getPublishedAt(),
        version.getCreatedAt());
  }
}
