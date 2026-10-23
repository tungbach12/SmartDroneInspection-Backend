package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.reportcontext.ReportContextAccess;
import com.smartdroneinspection.assets.reportcontext.ReportContextAccess.Identity;
import com.smartdroneinspection.inspections.api.dto.request.ReportSectionsRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse.DroneContext;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse.FieldSessionContext;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse.PreparationContext;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportVersionResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportSectionsResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.InspectionReportVersion;
import com.smartdroneinspection.inspections.domain.ReportSections;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.events.ReportPublishedEvent;
import com.smartdroneinspection.inspections.render.ReportHtmlTemplate;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.inspections.repository.InspectionReadinessDecisionRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportVersionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import tools.jackson.databind.node.ObjectNode;

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

  private static final String PROMPT_VERSION = "mf3-report-v3-bilingual";

  /** Recorded as the version model when the Inspector authors the draft without automated help. */
  private static final String MANUAL_MODEL_NAME = "manual-authoring";

  private static final String PDF_MEDIA_TYPE = "application/pdf";

  private final InspectionRepository inspections;
  private final InspectionPreparationRepository preparations;
  private final InspectionReadinessDecisionRepository readinessDecisions;
  private final ReportContextAccess reportContextAccess;
  private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
  private final EvidenceRepository evidence;
  private final FindingRepository findings;
  private final FieldSessionRepository fieldSessions;
  private final InspectionReportRepository reports;
  private final InspectionReportVersionRepository versions;
  private final EvidenceService evidenceService;
  private final Optional<ReportDraftPort> draftPort;
  private final ReportRenderPort renderPort;
  private final Optional<EvidenceObjectStore> objectStore;
  private final UserAccess userAccess;
  private final ApplicationEventPublisher events;
  private final ObjectMapper objectMapper;

  public ReportService(
      InspectionRepository inspections,
      InspectionPreparationRepository preparations,
      InspectionReadinessDecisionRepository readinessDecisions,
      ReportContextAccess reportContextAccess,
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
      EvidenceRepository evidence,
      FindingRepository findings,
      FieldSessionRepository fieldSessions,
      InspectionReportRepository reports,
      InspectionReportVersionRepository versions,
      EvidenceService evidenceService,
      Optional<ReportDraftPort> draftPort,
      ReportRenderPort renderPort,
      Optional<EvidenceObjectStore> objectStore,
      UserAccess userAccess,
      ApplicationEventPublisher events,
      ObjectMapper objectMapper) {
    this.inspections = inspections;
    this.preparations = preparations;
    this.readinessDecisions = readinessDecisions;
    this.reportContextAccess = reportContextAccess;
    this.jdbcTemplate = jdbcTemplate;
    this.evidence = evidence;
    this.fieldSessions = fieldSessions;
    this.findings = findings;
    this.reports = reports;
    this.versions = versions;
    this.evidenceService = evidenceService;
    this.draftPort = draftPort;
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
    return generateDraft(actorId, inspectionId, Locale.ENGLISH);
  }

  @Transactional
  public InspectionReportVersionResponse generateDraft(
      UUID actorId, UUID inspectionId, Locale locale) {
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

    List<Evidence> confirmedEvidence =
        evidence.findByInspectionIdAndRetiredAtIsNullOrderByCreatedAtAsc(inspectionId);
    List<UUID> currentEvidenceIds = confirmedEvidence.stream().map(Evidence::getId).toList();
    List<FieldSession> recordedSessions =
        fieldSessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    List<InspectionPreparation> recordedPreparations =
        preparations.findByInspectionIdOrderByPreparationVersionDesc(inspectionId);
    InspectionPreparation latestPreparation =
        recordedPreparations.stream().findFirst().orElse(null);
    InspectionReadinessDecision latestReadiness =
        readinessDecisions.findFirstByInspectionIdOrderByDecidedAtDesc(inspectionId).orElse(null);
    Identity reportIdentity =
        reportContextAccess
            .find(inspection.getOrganizationId(), inspection.getAssetId(), inspection.getDroneId())
            .orElse(null);
    List<Finding> confirmedFindings = inspectorFindings(inspectionId);

    // The drafting model is told about the suggestions the detector made. A finding carries that
    // provenance when it has one, so the suggestions are the findings that came from the model.
    List<Finding> detectionFindings =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(finding -> finding.getModelName() != null)
            .filter(finding -> currentEvidenceIds.contains(finding.getEvidenceId()))
            .toList();

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
                  buildChecklistSummaries(inspectionId, locale),
                  confirmedFindings.stream()
                      .map(
                          finding ->
                              new ReportDraftPort.FindingSummary(
                                  finding.getDefectLabel(),
                                  finding.getSeverity().name(),
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
                                  item.getFieldSessionId() == null
                                      ? null
                                      : item.getFieldSessionId().toString(),
                                  item.getLocationDescription()))
                      .toList(),
                  detectionFindings.stream()
                      .map(
                          detection ->
                              new ReportDraftPort.CandidateSummary(
                                  detection.getId().toString(),
                                  detection.getEvidenceId().toString(),
                                  detection.getModelProvider(),
                                  detection.getModelName(),
                                  detection.getModelVersion(),
                                  detection.getDefectLabel(),
                                  detection.getConfidence() == null
                                      ? null
                                      : detection.getConfidence().toPlainString(),
                                  detection.getBoundingBox(),
                                  detection.getStatus().name()))
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
                                  session.getPreFlightChecklistNote(),
                                  session.getPostponementReason(),
                                  session.getAbortReason(),
                                  session.getLimitations(),
                                  session.getDroneId() == null
                                      ? null
                                      : session.getDroneId().toString()))
                      .toList(),
                  locale));
    } catch (IOException ex) {
      // A failed generation permits a manual draft under the same review gates. No placeholder text
      // is written into a section: a section the author did not write and the model did not supply
      // stays absent, and reads as "not supplied" rather than as a confident blank.
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REPORT_DRAFT_UNAVAILABLE",
          "Automated drafting is temporarily unavailable");
    }

    int versionNo = report.nextVersionNumber();
    UUID sourceVersionId =
        versions.findByInspectionReportIdOrderByVersionNoDesc(report.getId()).stream()
            .findFirst()
            .map(InspectionReportVersion::getId)
            .orElse(null);
    InspectionReportVersion version =
        new InspectionReportVersion(
            report.getId(),
            versionNo,
            sourceVersionId,
            buildContentSnapshot(inspection, confirmedEvidence, confirmedFindings, proposal, port));
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
  public InspectionReportVersionResponse authorManualDraft(UUID actorId, UUID inspectionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    requireCurrentFindingsConfirmation(inspection);
    InspectionReport report = requireAuthoredReport(actorId, inspectionId);

    List<Evidence> confirmedEvidence =
        evidence.findByInspectionIdAndRetiredAtIsNullOrderByCreatedAtAsc(inspectionId);
    List<Finding> confirmedFindings = inspectorFindings(inspectionId);

    int versionNo = report.nextVersionNumber();
    UUID sourceVersionId =
        versions.findByInspectionReportIdOrderByVersionNoDesc(report.getId()).stream()
            .findFirst()
            .map(InspectionReportVersion::getId)
            .orElse(null);
    InspectionReportVersion version =
        new InspectionReportVersion(
            report.getId(),
            versionNo,
            sourceVersionId,
            buildManualContentSnapshot(inspection, confirmedEvidence, confirmedFindings));
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

  private List<Finding> inspectorFindings(UUID inspectionId) {
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .filter(Finding::isOfficial)
        .toList();
  }

  /**
   * MF3-06/09/10: only Inspector-confirmed findings are rendered; Admin reviews the report as a
   * whole.
   */
  private List<Finding> reportableFindings(UUID inspectionId) {
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .filter(Finding::isOfficial)
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
    version = requireVersionForAuthor(version.getId(), actorId);

    // Authoring is the author's work. A reviewer who is not the author may read a draft but cannot
    // rewrite it, and the author cannot write onto a version that has left an editable state.
    if (!report.getAuthorUserId().equals(actorId)) {
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
            request.inspectorCoverageAssessment(),
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
    version = requireVersionForAuthor(version.getId(), actorId);
    requireFreshEvidenceSnapshot(inspectionId, version);
    requireFreshFindingsSnapshot(inspectionId, version);
    requireCurrentFindingsConfirmation(
        evidenceService.requireAssignedInspection(actorId, inspectionId));
    try {
      version.verifyAsAuthor(actorId, report.getAuthorUserId());
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
    version = requireVersionForAuthor(version.getId(), actorId);
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

  private String currentFindingsSnapshotHash(UUID inspectionId) {
    List<Evidence> currentEvidence =
        evidence.findByInspectionIdAndRetiredAtIsNullOrderByCreatedAtAsc(inspectionId);
    return FindingsSnapshot.hash(
        currentEvidence, findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId));
  }

  /** A draft created before the latest findings confirmation is stale. */
  private void requireCurrentFindingsConfirmation(Inspection inspection) {
    if (!evidence.existsByInspectionIdAndRetiredAtIsNullAndUploadStatus(
        inspection.getId(), UploadStatus.AVAILABLE)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ACTIVE_EVIDENCE_REQUIRED",
          "A report draft requires at least one active evidence item");
    }
    String current = currentFindingsSnapshotHash(inspection.getId());
    if (!inspection.hasCurrentFindingsConfirmation(current)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_NOT_CONFIRMED",
          "Confirm the current findings before authoring a report");
    }
  }

  private void requireFreshFindingsSnapshot(UUID inspectionId, InspectionReportVersion version) {
    Inspection inspection = inspections.findById(inspectionId).orElse(null);
    if (inspection == null
        || inspection.getFindingsConfirmedAt() == null
        || version.getCreatedAt().isBefore(inspection.getFindingsConfirmedAt())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_SNAPSHOT_STALE",
          "Findings were confirmed after this draft was created; author a new draft version");
    }
  }

  private void requireFreshEvidenceSnapshot(UUID inspectionId, InspectionReportVersion version) {
    requireFreshFindingsSnapshot(inspectionId, version);
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
    if (!approve && (reason == null || reason.isBlank())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "REPORT_RETURN_REASON_REQUIRED",
          "A reviewer return requires a reason");
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

  /**
   * MF3-08: the author pulls a submission back before the reviewer acted on it. The version keeps
   * its number and content; the author revises and re-submits, and the reviewer never sees a
   * version change after they have acted on it.
   */
  @Transactional
  public InspectionReportVersionResponse withdrawVersion(
      UUID actorId, UUID inspectionId, UUID versionId, String reason) {
    evidenceService.requireAssignedInspection(actorId, inspectionId);
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    try {
      version.withdraw(actorId, reason, report.getAuthorUserId());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "REPORT_WITHDRAWAL_INVALID", ex.getMessage());
    }
    report.setStatus(ReportStatus.RETURNED);
    reports.saveAndFlush(report);
    Inspection inspection = inspections.findById(inspectionId).orElse(null);
    if (inspection != null) {
      inspection.reopenFindings();
      inspections.saveAndFlush(inspection);
    }
    return toResponse(versions.saveAndFlush(version));
  }

  /** MF3-11/12/13: publish, mark corrective items, and hand the approved findings to MF4. */
  @Transactional
  public ReportPublishedResponse publish(UUID actorId, UUID inspectionId, UUID versionId) {
    return publish(actorId, inspectionId, versionId, Locale.ENGLISH);
  }

  @Transactional
  public ReportPublishedResponse publish(
      UUID actorId, UUID inspectionId, UUID versionId, Locale locale) {
    UserAccess.ActiveUser publisher = requireQualifiedReviewer(actorId, inspectionId);
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    requireFreshEvidenceSnapshot(inspectionId, version);
    requireFreshFindingsSnapshot(inspectionId, version);
    try {
      version.publish(publisher.id(), report.getAuthorUserId());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }

    // MF3-11: the PDF is the version's deliverable, so it is rendered and stored before the version
    // can be marked published. A failure here rolls the publish back rather than leaving a
    // published
    // version with no artefact.
    storePublishedArtefact(inspectionId, version, locale);
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

    boolean hasConfirmedFindings =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .anyMatch(Finding::isOfficial);
    if (!hasConfirmedFindings) {
      inspection.complete();
    }
    inspections.saveAndFlush(inspection);

    return new ReportPublishedResponse(
        version.getId(),
        version.getVersionNo(),
        version.getPublishedAt(),
        List.of(),
        inspection.getStatus());
  }

  /** MF3-13 forwards selected, confirmed findings individually to MF4. */
  @Transactional
  public ReportPublishedResponse completeCorrectiveActionHandoff(
      UUID actorId, UUID inspectionId, List<UUID> selectedFindingIds) {
    UserAccess.ActiveUser reviewer = requireQualifiedReviewer(actorId, inspectionId);
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version =
        versions
            .findFirstByInspectionReportIdAndStatus(report.getId(), ReportStatus.PUBLISHED)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "REPORT_STATE_CONFLICT",
                        "Publish the report before forwarding findings to maintenance"));
    if (selectedFindingIds == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "FINDINGS_REQUIRED", "Provide the selected findings list");
    }
    if (inspection.getStatus() == InspectionStatus.COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_ALREADY_COMPLETED",
          "A completed inspection cannot change its maintenance decision");
    }
    if (inspection.getStatus() != InspectionStatus.REPORT_PUBLISHED
        && inspection.getStatus() != InspectionStatus.REPAIR_PENDING) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_STATE_CONFLICT",
          "Only a published report can forward findings to maintenance");
    }
    InspectionStatus beforeStatus = inspection.getStatus();
    if (selectedFindingIds.isEmpty()) {
      if (inspection.getStatus() == InspectionStatus.REPORT_PUBLISHED) {
        inspection.complete();
        inspections.saveAndFlush(inspection);
      }
      recordMaintenanceHandoff(inspection, reviewer.id(), version.getId(), beforeStatus, List.of());
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
    if (selectedFindingIds.stream().distinct().count() != selectedFindingIds.size()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "FINDINGS_INVALID", "A finding may only be selected once");
    }
    List<Finding> selected = new ArrayList<>();
    for (UUID findingId : selectedFindingIds) {
      Finding finding =
          findings
              .findByIdAndInspectionId(findingId, inspectionId)
              .filter(Finding::isOfficial)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "FINDING_NOT_FOUND",
                          "Selected finding is not an active confirmed finding in this inspection"));
      if (finding.isForwardedToMaintenance()) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "FINDING_ALREADY_FORWARDED",
            "Selected finding has already been forwarded to maintenance");
      }
      finding.forwardToMaintenance();
      selected.add(finding);
    }
    findings.saveAllAndFlush(selected);
    if (inspection.getStatus() == InspectionStatus.REPORT_PUBLISHED) {
      inspection.markRepairPending();
      inspections.saveAndFlush(inspection);
    }
    List<UUID> forwardedFindingIds = selected.stream().map(Finding::getId).toList();
    recordMaintenanceHandoff(
        inspection, reviewer.id(), version.getId(), beforeStatus, forwardedFindingIds);
    events.publishEvent(
        new ReportPublishedEvent(
            report.getId(),
            version.getId(),
            inspectionId,
            inspection.getOrganizationId(),
            inspection.getAssetId(),
            reviewer.id(),
            forwardedFindingIds,
            version.getPublishedAt()));
    return new ReportPublishedResponse(
        version.getId(),
        version.getVersionNo(),
        version.getPublishedAt(),
        forwardedFindingIds,
        inspection.getStatus());
  }

  private void recordMaintenanceHandoff(
      Inspection inspection,
      UUID actorId,
      UUID reportVersionId,
      InspectionStatus beforeStatus,
      List<UUID> forwardedFindingIds) {
    String findingIdsJson =
        forwardedFindingIds.stream()
            .map(id -> "\"" + id + "\"")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    jdbcTemplate.update(
        """
        INSERT INTO audit_events
          (organization_id, actor_user_id, action, aggregate_type, aggregate_id,
           before_status, after_status, safe_metadata, created_at)
        VALUES (?, ?, 'MF3_MAINTENANCE_HANDOFF', 'INSPECTION', ?, ?, ?,
                jsonb_build_object('reportVersionId', ?, 'forwardedFindingIds', ?::jsonb), CURRENT_TIMESTAMP)
        """,
        inspection.getOrganizationId(),
        actorId,
        inspection.getId(),
        beforeStatus.name(),
        inspection.getStatus().name(),
        reportVersionId,
        findingIdsJson);
  }

  /**
   * MF3-11: renders the deliverable, stores it, and records its checksum on the version.
   *
   * <p>The object key is derived from the version id, so publishing a later version never
   * overwrites the bytes stored for an already-published one. The checksum is taken from the bytes
   * that were stored, so it describes what a later reader would actually download.
   */
  private void storePublishedArtefact(
      UUID inspectionId, InspectionReportVersion version, Locale locale) {
    byte[] pdf = renderArtefact(inspectionId, version, false, locale);
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
    return preview(actorId, inspectionId, versionId, Locale.ENGLISH);
  }

  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> preview(
      UUID actorId, UUID inspectionId, UUID versionId, Locale locale) {
    InspectionReport report = requireReportForInspection(inspectionId);
    InspectionReportVersion version = requireVersion(report.getId(), versionId);
    if (!report.getAuthorUserId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the report author may preview this draft");
    }
    return pdfResponse(
        renderArtefact(inspectionId, version, true, locale),
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
      UUID inspectionId, InspectionReportVersion version, boolean preview, Locale locale) {
    try {
      return renderPort.render(buildRenderContext(inspectionId, version, preview, locale));
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
   * <p>Inspector-confirmed findings and evidence are read from their own records at render time
   * rather than from the stored body, so the document cannot disagree with source records. Evidence
   * bytes are loaded here so the PDF embeds the stored original rather than a reference to it.
   */
  private ReportRenderPort.RenderContext buildRenderContext(
      UUID inspectionId, InspectionReportVersion version, boolean preview, Locale locale) {
    Inspection inspection = inspections.findById(inspectionId).orElseThrow();
    Identity reportIdentity =
        reportContextAccess
            .find(inspection.getOrganizationId(), inspection.getAssetId(), inspection.getDroneId())
            .orElse(null);
    var preparation =
        preparations
            .findFirstByInspectionIdOrderByPreparationVersionDesc(inspectionId)
            .orElse(null);
    var readiness =
        readinessDecisions.findFirstByInspectionIdOrderByDecidedAtDesc(inspectionId).orElse(null);
    DroneContext assignedDrone =
        reportIdentity == null || reportIdentity.droneSerialNumber() == null
            ? null
            : new DroneContext(
                reportIdentity.droneSerialNumber(),
                reportIdentity.droneManufacturer(),
                reportIdentity.droneModel());
    List<FieldSession> recordedSessions =
        fieldSessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    ReportRenderPort.ReportContext renderReportContext =
        buildRenderReportContext(
    ReportRenderPort.ReportContext renderReportContext =
        buildRenderReportContext(
            inspection,
            reportIdentity,
            assignedDrone,
            preparation,
            readiness,
            recordedSessions,
            locale);
    ReportSections sections = ReportSections.read(version.getContentSnapshot(), objectMapper);
    JsonNode snapshot;
    try {
      snapshot = objectMapper.readTree(version.getContentSnapshot());
    } catch (JacksonException exception) {
      snapshot = objectMapper.createObjectNode();
    }
    String purposeScopeCriteria = composePurposeScopeCriteria(inspection, snapshot, locale);

    List<ReportRenderPort.FindingView> findingViews = new ArrayList<>();
    Map<UUID, List<String>> componentLocationByEvidence = new LinkedHashMap<>();
    Map<UUID, Evidence> evidenceById = new LinkedHashMap<>();
    for (Evidence item :
        evidence.findByInspectionIdAndRetiredAtIsNullOrderByCreatedAtAsc(inspectionId)) {
      evidenceById.put(item.getId(), item);
    }
    List<Finding> confirmed = reportableFindings(inspectionId);
    for (Finding finding : confirmed) {
      List<String> evidenceRefs = new ArrayList<>();
      if (finding.getEvidenceId() != null) {
        evidenceRefs.add(finding.getEvidenceId().toString());
        Evidence evItem = evidenceById.get(finding.getEvidenceId());
        if (evItem != null) {
          evidenceRefs.add(evItem.getFileName());
        }
      }
      findingViews.add(
          new ReportRenderPort.FindingView(
              finding.getId().toString(),
              finding.getEvidenceId() == null ? null : finding.getEvidenceId().toString(),
              finding.getEvidenceId() == null || !evidenceById.containsKey(finding.getEvidenceId())
                  ? null
                  : evidenceById.get(finding.getEvidenceId()).getFileName(),
              finding.getDefectLabel(),
              finding.getSeverity().name(),
              finding.getTechnicalNotes(),
              finding.getRecommendedAction(),
              finding.getStatus().name(),
              finding.getInspectorConfirmedAt() == null
                  ? null
                  : finding.getInspectorConfirmedAt().toString(),
              finding.isOfficial()));
    }

    List<ReportRenderPort.EvidenceView> evidenceViews = new ArrayList<>();
    for (Evidence item :
        evidence.findByInspectionIdAndRetiredAtIsNullOrderByCreatedAtAsc(inspectionId)) {
      byte[] content = loadEvidenceBytes(item);
      evidenceViews.add(
          new ReportRenderPort.EvidenceView(
              item.getId().toString(),
              item.getFileName(),
              item.getContentType(),
              item.getCaptureTime() == null ? null : item.getCaptureTime().toString(),
              item.getLocationDescription(),
              item.getFieldSessionId() == null ? null : item.getFieldSessionId().toString(),
              content));
    }

    return new ReportRenderPort.RenderContext(
        documentReference(version),
        version.getVersionNo(),
        version.getStatus().name(),
        inspection.getObjective(),
        reportIdentity == null || reportIdentity.assetName() == null
            ? null
            : reportIdentity.assetName(),
        reportIdentity == null || reportIdentity.organizationName() == null
            ? null
            : reportIdentity.organizationName(),
        displayName(reports.findByInspectionId(inspectionId).orElseThrow().getAuthorUserId()),
        displayName(version.getReviewerUserId()),
        inspection.getFindingsConfirmedAt() == null
            ? null
            : inspection.getFindingsConfirmedAt().toString(),
        renderReportContext,
        version.getSubmittedAt() == null ? null : version.getSubmittedAt().toString(),
        version.getReviewedAt() == null ? null : version.getReviewedAt().toString(),
        version.getPublishedAt() == null ? null : version.getPublishedAt().toString(),
        new ReportRenderPort.Sections(
            sections.executiveSummary(),
            purposeScopeCriteria,
            sections.inspectorCoverageAssessment(),
            sections.methodEquipmentConditions(),
            sections.conclusionsRecommendations(),
            sections.overallCondition() == null ? null : sections.overallCondition().name(),
            sections.omissionDisclosure()),
        findingViews,
        evidenceViews,
        locale);
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
        .filter(candidate -> candidate.getInspectionReportId().equals(reportId))
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "REPORT_VERSION_NOT_FOUND", "Report version not found"));
  }

  private InspectionReport requireReportForInspectionFromId(UUID reportId) {
    return reports
        .findById(reportId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Inspection report not found"));
  }

  private InspectionReportVersion requireVersionForAuthor(UUID versionId, UUID authorUserId) {
    InspectionReportVersion version =
        versions
            .findById(versionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "REPORT_VERSION_NOT_FOUND",
                        "Report version not found"));
    InspectionReport report = requireReportForInspectionFromId(version.getInspectionReportId());
    if (!report.getAuthorUserId().equals(authorUserId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the report author may access this version");
    }
    return version;
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
      List<Finding> confirmedFindings,
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
   * The manual variant records generation provenance; it is not entered as an inspection
   * limitation.
   */
  private String buildManualContentSnapshot(
      Inspection inspection, List<Evidence> confirmedEvidence, List<Finding> confirmedFindings) {
    ObjectNode snapshot = objectMapper.createObjectNode();
    snapshot.put("model", MANUAL_MODEL_NAME);
    snapshot.put("assetId", inspection.getAssetId().toString());
    snapshot.put("inspectionId", inspection.getId().toString());
    snapshot.put("evidenceCount", confirmedEvidence.size());
    snapshot.put("findingsCount", confirmedFindings.size());
    snapshot.put("analysisMethod", "MANUAL");
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Could not create a manual report snapshot", exception);
    }
  }

  private String composePurposeScopeCriteria(
      Inspection inspection, JsonNode snapshot, Locale locale) {
    StringBuilder content = new StringBuilder();
    appendStructuredValue(
        content,
        ReportHtmlTemplate.localized(locale, "Objective"),
        objectMapper.getNodeFactory().textNode(inspection.getObjective()));
    appendStructuredValue(
        content,
        ReportHtmlTemplate.localized(locale, "Scope"),
        jsonField(inspection.getScope(), "summary"));
    appendArrayValues(
        content,
        ReportHtmlTemplate.localized(locale, "Included components"),
        inspection.getComponentScope(),
        "items");
    appendArrayValues(
        content,
        ReportHtmlTemplate.localized(locale, "Acceptance criteria"),
        inspection.getAcceptanceCriteria(),
        "criteria");
    preparations
        .findFirstByInspectionIdOrderByPreparationVersionDesc(inspection.getId())
        .map(preparation -> parseJson(preparation.getShotList()))
        .ifPresent(shotList -> appendShotList(content, shotList, locale));
    return content.isEmpty() ? null : content.toString();
  }

  private JsonNode jsonField(String raw, String field) {
    JsonNode node = parseJson(raw);
    if (node == null) return null;
    JsonNode value = node.get(field);
    return value == null ? node : value;
  }

  private JsonNode parseJson(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return objectMapper.readTree(raw);
    } catch (JacksonException exception) {
      return objectMapper.getNodeFactory().textNode(raw);
    }
  }

  private void appendArrayValues(StringBuilder content, String label, String raw, String field) {
    JsonNode node = jsonField(raw, field);
    if (node == null || node.isNull()) return;
    content.append(label).append(':');
    if (node.isArray()) {
      node.forEach(
          item ->
              content
                  .append("\n• ")
                  .append(item.isValueNode() ? item.asString("") : item.toString()));
    } else {
      content.append("\n• ").append(node.isValueNode() ? node.asString("") : node.toString());
    }
    content.append('\n');
  }

  private void appendShotList(StringBuilder content, JsonNode shotList, Locale locale) {
    if (shotList == null || !shotList.isArray() || shotList.isEmpty()) return;
    content.append(ReportHtmlTemplate.localized(locale, "Planned shot-list"));
    content.append(':');
    shotList.forEach(
        item -> {
          String code = item.path("code").asString("");
          String angle = item.path("angle").asString("");
          if (!code.isBlank() || !angle.isBlank()) {
            content.append("\n• ").append(code);
            if (!angle.isBlank()) content.append(" — ").append(angle);
            if (item.path("required").asBoolean(false)) {
              content.append(ReportHtmlTemplate.localized(locale, " (required)"));
            }
          }
        });
    content.append('\n');
  }

  private void appendStructuredValue(StringBuilder content, String label, JsonNode value) {
    if (value == null || value.isNull() || value.isMissingNode()) return;
    String text = value.isValueNode() ? value.asString("") : value.toString();
    if (text.isBlank()) return;
    content.append(label).append(": ").append(text).append('\n');
  }

  private List<ReportDraftPort.ChecklistEntrySummary> buildChecklistSummaries(UUID inspectionId) {
    return buildChecklistSummaries(inspectionId, Locale.ENGLISH);
  }

  private List<ReportDraftPort.ChecklistEntrySummary> buildChecklistSummaries(
      UUID inspectionId, Locale locale) {
    return jdbcTemplate.query(
        "SELECT item.prompt, item.prompt_vi, COALESCE(response.answer, response.response_value)::text AS answer, response.notes "
            + "FROM checklist_responses response "
            + "JOIN field_sessions session "
            + "  ON session.id = response.field_session_id "
            + " AND session.inspection_id = response.inspection_id "
            + "JOIN checklist_items item "
            + "  ON item.id = response.checklist_item_id "
            + " AND item.template_id = session.checklist_template_id "
            + "WHERE response.inspection_id = ? "
            + "ORDER BY response.completed_at NULLS LAST, response.created_at",
        (result, rowNum) -> {
          String englishPrompt = result.getString("prompt");
          String vietnamesePrompt = result.getString("prompt_vi");
          String prompt =
              locale != null
                      && "vi".equalsIgnoreCase(locale.getLanguage())
                      && vietnamesePrompt != null
                      && !vietnamesePrompt.isBlank()
                  ? vietnamesePrompt
                  : englishPrompt;
          return new ReportDraftPort.ChecklistEntrySummary(
              prompt, responseLabel(result.getString("answer")), result.getString("notes"));
        },
        inspectionId);
  }

  private String responseLabel(String rawAnswer) {
    if (rawAnswer == null || rawAnswer.isBlank()) return null;
    try {
      JsonNode answer = objectMapper.readTree(rawAnswer);
      if (answer.isValueNode()) return answer.asString();
      for (String key : List.of("value", "answer", "response", "selectedValue")) {
        JsonNode value = answer.get(key);
        if (value != null && value.isValueNode()) return value.asString();
      }
      return answer.toString();
    } catch (JacksonException exception) {
      return rawAnswer;
    }
  }

  private String evidenceByFindingSession(Finding finding, List<Evidence> currentEvidence) {
    return finding.getEvidenceId() == null
        ? null
        : currentEvidence.stream()
            .filter(item -> item.getId().equals(finding.getEvidenceId()))
            .map(Evidence::getFieldSessionId)
            .filter(Objects::nonNull)
            .map(UUID::toString)
            .findFirst()
            .orElse(null);
  }

  private InspectionReportContextResponse buildReportContextResponse(
      Inspection inspection,
      Identity identity,
      DroneContext drone,
      InspectionPreparation preparation,
      InspectionReadinessDecision readiness,
      List<FieldSession> sessions) {
    PreparationContext preparationContext =
        preparation == null
            ? null
            : new PreparationContext(
                preparation.getPreparationVersion(),
                preparation.getStatus().name(),
                preparation.getShotList(),
                preparation.getEvidenceTypes(),
                preparation.getAccessConstraints(),
                preparation.getSafetyObservations(),
                readiness == null ? null : readiness.getDecision().name(),
                readiness == null ? null : readiness.getDecidedAt(),
                readiness == null ? null : readiness.getReason(),
                preparation.getPermitDocumentReferences(),
                preparation.getSubmittedAt());
    return new InspectionReportContextResponse(
        identity == null ? null : identity.organizationName(),
        identity == null ? null : identity.assetCode(),
        identity == null ? null : identity.assetName(),
        identity == null ? null : identity.assetLocation(),
        identity == null ? null : identity.assetCategory(),
        drone,
        preparationContext,
        sessions.stream()
            .map(
                session ->
                    new FieldSessionContext(
                        session.getId(),
                        session.getStatus(),
                        session.getStartedAt(),
                        session.getEndedAt(),
                        session.getPreFlightChecklistNote(),
                        session.getPostponementReason(),
                        session.getAbortReason(),
                        session.getOutcomeNotes(),
                        session.getLimitations(),
                        drone == null ? null : drone.serialNumber(),
                        drone == null ? null : drone.manufacturer(),
                        drone == null ? null : drone.model()))
            .toList(),
        "Application role/organization/separation checks ran; professional qualification was not independently validated.",
        inspection.getFindingsConfirmedAt(),
        inspection.getFindingsSnapshotHash());
  }

  private ReportRenderPort.ReportContext buildRenderReportContext(
      Inspection inspection,
      Identity identity,
      DroneContext drone,
      InspectionPreparation preparation,
      InspectionReadinessDecision readiness,
  private ReportRenderPort.ReportContext buildRenderReportContext(
      Inspection inspection,
      ReportIdentity identity,
      DroneContext drone,
      InspectionPreparation preparation,
      InspectionReadinessDecision readiness,
      List<FieldSession> sessions,
      Locale locale) {
    InspectionPreparation prep = preparation;
    return new ReportRenderPort.ReportContext(
        identity == null ? null : identity.organizationName(),
        identity == null ? null : identity.assetCode(),
        identity == null ? null : identity.assetName(),
        identity == null ? null : identity.assetLocation(),
        identity == null ? null : identity.assetCategory(),
        drone == null ? null : drone.serialNumber(),
        drone == null ? null : drone.manufacturer(),
        drone == null ? null : drone.model(),
        prep == null ? null : prep.getStatus().name(),
        prep == null ? null : prep.getShotList(),
        prep == null ? null : prep.getEvidenceTypes(),
        prep == null ? null : prep.getAccessConstraints(),
        prep == null ? null : prep.getSafetyObservations(),
        prep == null ? null : prep.getPermitDocumentReferences(),
        readiness == null ? null : readiness.getDecision().name(),
        readiness == null || readiness.getDecidedAt() == null
            ? null
            : readiness.getDecidedAt().toString(),
        readiness == null ? null : readiness.getReason(),
        buildChecklistSummaries(inspection.getId(), locale).stream()
            .map(
                entry ->
                    ReportHtmlTemplate.localized(locale, "Checklist response")
                        + ": "
                        + entry.prompt()
                        + " — "
                        + entry.responseValue()
                        + (entry.notes() == null
                            ? ""
                            : " ("
                                + ReportHtmlTemplate.localized(locale, "Notes")
                                + ": "
                                + entry.notes()
                                + ")"))
            .toList(),
        sessions.stream().map(session -> fieldSessionSummary(session, drone, locale)).toList(),
        ReportHtmlTemplate.localized(
            locale,
            "Reviewer role and separation were recorded; professional credential qualification was not validated by the application."));
  }

  private String fieldSessionSummary(FieldSession session, DroneContext drone, Locale locale) {
    return java.util.stream.Stream.of(
            ReportHtmlTemplate.localized(locale, "Session ") + session.getId(),
            ReportHtmlTemplate.localized(locale, "Status") + ": " + session.getStatus(),
            contextField(locale, "Started", session.getStartedAt()),
            contextField(locale, "Ended", session.getEndedAt()),
            contextField(locale, "Pre-flight checklist note", session.getPreFlightChecklistNote()),
            contextField(locale, "Postponement reason", session.getPostponementReason()),
            contextField(locale, "Abort reason", session.getAbortReason()),
            contextField(locale, "Field-session outcome", session.getOutcomeNotes()),
            contextField(locale, "Recorded limitations", session.getLimitations()),
            contextField(locale, "Assigned Drone", drone == null ? null : droneLabel(drone)))
        .filter(Objects::nonNull)
        .collect(java.util.stream.Collectors.joining(" · "));
  }

  private static String contextField(Locale locale, String label, Object value) {
    if (value == null || value.toString().isBlank()) return null;
    return ReportHtmlTemplate.localized(locale, label) + ": " + value;
  }

  private static String droneLabel(DroneContext drone) {
    return java.util.stream.Stream.of(drone.manufacturer(), drone.model(), drone.serialNumber())
        .filter(value -> value != null && !value.isBlank())
        .collect(java.util.stream.Collectors.joining(" · "));
  }

  private InspectionReportContextResponse reportContextForVersion(InspectionReportVersion version) {
    InspectionReport report = reports.findById(version.getInspectionReportId()).orElse(null);
    if (report == null) return null;
    Inspection inspection = inspections.findById(report.getInspectionId()).orElse(null);
    if (inspection == null) return null;
    var identity =
        reportContextAccess
            .find(inspection.getOrganizationId(), inspection.getAssetId(), inspection.getDroneId())
            .orElse(null);
    var drone =
        identity == null || identity.droneSerialNumber() == null
            ? null
            : new DroneContext(
                identity.droneSerialNumber(), identity.droneManufacturer(), identity.droneModel());
    var prep =
        preparations
            .findFirstByInspectionIdOrderByPreparationVersionDesc(inspection.getId())
            .orElse(null);
    var readiness =
        readinessDecisions
            .findFirstByInspectionIdOrderByDecidedAtDesc(inspection.getId())
            .orElse(null);
    var sessions = fieldSessions.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
    return buildReportContextResponse(inspection, identity, drone, prep, readiness, sessions);
  }

  private static String quote(String value) {
    if (value == null) {
      return "null";
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
  }

  private InspectionReportVersionResponse toResponse(InspectionReportVersion version) {
    JsonNode content = readContentSnapshot(version.getContentSnapshot());
    InspectionReport report = reports.findById(version.getInspectionReportId()).orElseThrow();
    return new InspectionReportVersionResponse(
        version.getId(),
        version.getInspectionReportId(),
        version.getVersionNo(),
        version.getStatus(),
        report.getAuthorUserId(),
        version.getSubmittedAt(),
        version.getReviewerUserId(),
        version.getReviewedAt(),
        version.getReturnReason(),
        version.getWithdrawnAt(),
        version.getWithdrawalReason(),
        version.getPublishedAt(),
        version.getCreatedAt(),
        textOrNull(content, "omissionDisclosure"),
        version.getContentSnapshot(),
        ReportSectionsResponse.from(
            ReportSections.read(version.getContentSnapshot(), objectMapper)),
        reportContextForVersion(version));
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
