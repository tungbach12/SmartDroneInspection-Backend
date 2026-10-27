package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.reportcontext.ReportContextAccess;
import com.smartdroneinspection.assets.reportcontext.ReportContextAccess.Identity;
import com.smartdroneinspection.inspections.api.dto.request.ReportSectionsRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse.DroneContext;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse.FieldSessionContext;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportSectionsResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.ReportSections;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.events.ReportPublishedEvent;
import com.smartdroneinspection.inspections.render.ReportHtmlTemplate;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
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
  private final ReportContextAccess reportContextAccess;
  private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
  private final EvidenceRepository evidence;
  private final FindingRepository findings;
  private final FieldSessionRepository fieldSessions;
  private final InspectionReportRepository versions;
  private final EvidenceService evidenceService;
  private final Optional<ReportDraftPort> draftPort;
  private final ReportRenderPort renderPort;
  private final Optional<EvidenceObjectStore> objectStore;
  private final UserAccess userAccess;
  private final ApplicationEventPublisher events;
  private final ObjectMapper objectMapper;

  public ReportService(
      InspectionRepository inspections,
      ReportContextAccess reportContextAccess,
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
      EvidenceRepository evidence,
      FindingRepository findings,
      FieldSessionRepository fieldSessions,
      InspectionReportRepository versions,
      EvidenceService evidenceService,
      Optional<ReportDraftPort> draftPort,
      ReportRenderPort renderPort,
      Optional<EvidenceObjectStore> objectStore,
      UserAccess userAccess,
      ApplicationEventPublisher events,
      ObjectMapper objectMapper) {
    this.inspections = inspections;
    this.reportContextAccess = reportContextAccess;
    this.jdbcTemplate = jdbcTemplate;
    this.evidence = evidence;
    this.fieldSessions = fieldSessions;
    this.findings = findings;
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
  public InspectionReportResponse generateDraft(UUID actorId, UUID inspectionId) {
    return generateDraft(actorId, inspectionId, Locale.ENGLISH);
  }

  @Transactional
  public InspectionReportResponse generateDraft(UUID actorId, UUID inspectionId, Locale locale) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);

    requireCurrentFindingsConfirmation(inspection);

    InspectionReport latestExisting =
        versions.findFirstByInspectionIdOrderByVersionNoDesc(inspectionId).orElse(null);
    if (latestExisting != null && !inspection.getInspectorId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the assigned Inspector authors this report");
    }

    List<Evidence> confirmedEvidence =
        evidence.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(inspectionId);
    List<UUID> currentEvidenceIds = confirmedEvidence.stream().map(Evidence::getId).toList();
    List<FieldSession> recordedSessions =
        fieldSessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
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
                      reportIdentity == null ? List.of() : reportIdentity.inspectionItems(),
                      inspection.getFlightPermitFileName(),
                      inspection.getPlannedStartAt() == null
                          ? null
                          : inspection.getPlannedStartAt().toString(),
                      inspection.getPlannedEndAt() == null
                          ? null
                          : inspection.getPlannedEndAt().toString(),
                      inspection.getDroneId() == null ? null : inspection.getDroneId().toString()),
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
                                  null,
                                  detection.getModelName(),
                                  null,
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
                                  inspection.getDroneId() == null
                                      ? null
                                      : inspection.getDroneId().toString()))
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

    int versionNo = latestExisting == null ? 1 : latestExisting.getVersionNo() + 1;
    InspectionReport version =
        new InspectionReport(
            inspectionId,
            versionNo,
            buildContentSnapshot(inspection, confirmedEvidence, confirmedFindings, proposal, port));
    return toResponse(versions.saveAndFlush(version), inspection.getInspectorId());
  }

  /**
   * MF3-08 manual authoring. Report 3 requires that a failed or unconfigured drafting service still
   * lets the author complete a structured draft, so this creates the same version record under the
   * same review gates and records that no automated drafting contributed to it.
   */
  @Transactional
  public InspectionReportResponse authorManualDraft(UUID actorId, UUID inspectionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    requireCurrentFindingsConfirmation(inspection);
    InspectionReport latestExisting =
        versions.findFirstByInspectionIdOrderByVersionNoDesc(inspectionId).orElse(null);
    if (latestExisting != null && !inspection.getInspectorId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the assigned Inspector authors this report");
    }

    List<Evidence> confirmedEvidence =
        evidence.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(inspectionId);
    List<Finding> confirmedFindings = inspectorFindings(inspectionId);

    int versionNo = latestExisting == null ? 1 : latestExisting.getVersionNo() + 1;
    InspectionReport version =
        new InspectionReport(
            inspectionId,
            versionNo,
            buildManualContentSnapshot(inspection, confirmedEvidence, confirmedFindings));
    return toResponse(versions.saveAndFlush(version), inspection.getInspectorId());
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
  public InspectionReportResponse saveSections(
      UUID actorId, UUID inspectionId, UUID versionId, ReportSectionsRequest request) {
    InspectionReport version = requireVersion(inspectionId, versionId);
    Inspection inspection =
        inspections.findById(inspectionId).orElseThrow(this::inspectionNotFound);
    if (!inspection.getInspectorId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the report author may edit this draft");
    }

    requireCurrentFindingsConfirmation(inspection);
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
    return toResponse(versions.saveAndFlush(version), inspection.getInspectorId());
  }

  /** MF3-08: the author verifies the draft against its sources. */
  @Transactional
  public InspectionReportResponse verifyVersion(UUID actorId, UUID inspectionId, UUID versionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    InspectionReport version = requireVersion(inspectionId, versionId);
    requireFreshEvidenceSnapshot(inspectionId, version);
    requireFreshFindingsSnapshot(inspectionId, version);
    requireCurrentFindingsConfirmation(inspection);
    try {
      version.verifyAsAuthor(actorId, inspection.getInspectorId());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }
    return toResponse(versions.saveAndFlush(version), inspection.getInspectorId());
  }

  /** MF3-08: submit the verified version for review. */
  @Transactional
  public InspectionReportResponse submitVersion(UUID actorId, UUID inspectionId, UUID versionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    InspectionReport version = requireVersion(inspectionId, versionId);
    requireFreshEvidenceSnapshot(inspectionId, version);
    requireFreshFindingsSnapshot(inspectionId, version);
    requireCurrentFindingsConfirmation(inspection);
    try {
      version.submit();
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }
    return toResponse(versions.saveAndFlush(version), inspection.getInspectorId());
  }

  private String currentFindingsSnapshotHash(UUID inspectionId) {
    List<Evidence> currentEvidence =
        evidence.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(inspectionId);
    return FindingsSnapshot.hash(
        currentEvidence, findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId));
  }

  /** A draft created before the latest findings confirmation is stale. */
  private void requireCurrentFindingsConfirmation(Inspection inspection) {
    if (!evidence.existsByInspectionIdAndIsDeletedFalse(inspection.getId())) {
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

  private void requireFreshFindingsSnapshot(UUID inspectionId, InspectionReport version) {
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

  private void requireFreshEvidenceSnapshot(UUID inspectionId, InspectionReport version) {
    requireFreshFindingsSnapshot(inspectionId, version);
  }

  /**
   * MF3-09: the qualified reviewer decides. Only an in-organization ORG_MANAGER who is not the
   * author may review, and returning a draft requires a reason.
   */
  @Transactional
  public InspectionReportResponse reviewVersion(
      UUID actorId, UUID inspectionId, UUID versionId, boolean approve, String reason) {
    UserAccess.ActiveUser reviewer = requireQualifiedReviewer(actorId, inspectionId);
    InspectionReport version = requireVersion(inspectionId, versionId);
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
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    return toResponse(versions.saveAndFlush(version), inspection.getInspectorId());
  }

  @Transactional
  public InspectionReportResponse withdrawVersion(UUID actorId, UUID inspectionId, UUID versionId) {
    return withdrawVersion(actorId, inspectionId, versionId, null);
  }

  /**
   * MF3-08: the author pulls a submission back before the reviewer acted on it. The version keeps
   * its number and content; the author revises and re-submits, and the reviewer never sees a
   * version change after they have acted on it.
   */
  @Transactional
  public InspectionReportResponse withdrawVersion(
      UUID actorId, UUID inspectionId, UUID versionId, String reason) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    InspectionReport version = requireVersion(inspectionId, versionId);
    try {
      version.withdraw(actorId, inspection.getInspectorId());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "REPORT_WITHDRAWAL_INVALID", ex.getMessage());
    }
    versions.saveAndFlush(version);
    inspection.reopenFindings();
    inspections.saveAndFlush(inspection);
    return toResponse(version, inspection.getInspectorId());
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
    InspectionReport version = requireVersion(inspectionId, versionId);
    requireFreshEvidenceSnapshot(inspectionId, version);
    requireFreshFindingsSnapshot(inspectionId, version);
    try {
      version.publish(publisher.id(), inspection.getInspectorId());
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
    List<InspectionReport> previouslyPublished =
        versions.findByInspectionIdAndStatus(inspectionId, ReportStatus.PUBLISHED);
    for (InspectionReport prev : previouslyPublished) {
      if (!prev.getId().equals(version.getId())) {
        prev.supersede();
        versions.save(prev);
      }
    }

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
    InspectionReport version =
        versions
            .findFirstByInspectionIdAndStatus(inspectionId, ReportStatus.PUBLISHED)
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
              version.getId(),
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
            version.getId(),
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
  private void storePublishedArtefact(UUID inspectionId, InspectionReport version, Locale locale) {
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
    Inspection inspection =
        inspections.findById(inspectionId).orElseThrow(this::inspectionNotFound);
    if (!inspection.getInspectorId().equals(actorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the report author may preview this draft");
    }
    InspectionReport version = requireVersion(inspectionId, versionId);
    return pdfResponse(
        renderArtefact(inspectionId, version, true, locale),
        "inline",
        documentReference(version) + "-preview.pdf");
  }

  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> downloadArtefact(UUID actorId, UUID inspectionId, UUID versionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    InspectionReport version = requireVersion(inspectionId, versionId);
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
      UUID inspectionId, InspectionReport version, boolean preview, Locale locale) {
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

  private static String documentReference(InspectionReport version) {
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
      UUID inspectionId, InspectionReport version, boolean preview, Locale locale) {
    Inspection inspection = inspections.findById(inspectionId).orElseThrow();
    Identity reportIdentity =
        reportContextAccess
            .find(inspection.getOrganizationId(), inspection.getAssetId(), inspection.getDroneId())
            .orElse(null);
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
            inspection, reportIdentity, assignedDrone, recordedSessions, locale);
    ReportSections sections = ReportSections.read(version.getContentSnapshot(), objectMapper);
    String purposeScopeCriteria = composePurposeScopeCriteria(inspection, reportIdentity, locale);

    List<ReportRenderPort.FindingView> findingViews = new ArrayList<>();
    Map<UUID, List<String>> componentLocationByEvidence = new LinkedHashMap<>();
    Map<UUID, Evidence> evidenceById = new LinkedHashMap<>();
    for (Evidence item :
        evidence.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(inspectionId)) {
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
              null,
              finding.isOfficial()));
    }

    List<ReportRenderPort.EvidenceView> evidenceViews = new ArrayList<>();
    for (Evidence item :
        evidence.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(inspectionId)) {
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
        reportIdentity == null ? null : reportIdentity.assetName(),
        reportIdentity == null || reportIdentity.assetName() == null
            ? null
            : reportIdentity.assetName(),
        reportIdentity == null || reportIdentity.organizationName() == null
            ? null
            : reportIdentity.organizationName(),
        displayName(inspection.getInspectorId()),
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
  public List<InspectionReportResponse> listVersions(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    // An inspection whose evidence is not yet accepted has no report at all. That is a normal
    // empty state for the reviewer, not a missing resource, so it is reported as an empty list.
    return versions.findByInspectionIdOrderByVersionNoDesc(inspectionId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public InspectionReportResponse getVersion(UUID actorId, UUID inspectionId, UUID versionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return toResponse(requireVersion(inspectionId, versionId));
  }

  private UserAccess.ActiveUser requireQualifiedReviewer(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
    if (actor.organizationId() == null || !actor.hasRole("ORG_MANAGER")) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "REPORT_SCOPE_DENIED", "Only a qualified ORG_MANAGER may review");
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

  private InspectionReport requireVersion(UUID inspectionId, UUID versionId) {
    return versions
        .findById(versionId)
        .filter(candidate -> candidate.getInspectionId().equals(inspectionId))
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "REPORT_VERSION_NOT_FOUND", "Report version not found"));
  }

  private BusinessException inspectionNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found");
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
      Inspection inspection, Identity identity, Locale locale) {
    StringBuilder content = new StringBuilder();
    if (inspection.getFlightPermitFileName() != null
        && !inspection.getFlightPermitFileName().isBlank()) {
      content
          .append(ReportHtmlTemplate.localized(locale, "Flight permit"))
          .append(": ")
          .append(inspection.getFlightPermitFileName())
          .append('\n');
    }
    if (identity != null
        && identity.inspectionItems() != null
        && !identity.inspectionItems().isEmpty()) {
      content.append(ReportHtmlTemplate.localized(locale, "Included components")).append(":\n");
      for (String item : identity.inspectionItems()) {
        content.append(" - ").append(item).append('\n');
      }
    }
    return content.isEmpty() ? null : content.toString();
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
      Inspection inspection, Identity identity, DroneContext drone, List<FieldSession> sessions) {
    return new InspectionReportContextResponse(
        identity == null ? null : identity.organizationName(),
        identity == null ? null : identity.assetCode(),
        identity == null ? null : identity.assetName(),
        identity == null ? null : identity.assetLocation(),
        identity == null ? null : identity.assetCategory(),
        drone,
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
      List<FieldSession> sessions,
      Locale locale) {
    return new ReportRenderPort.ReportContext(
        identity == null ? null : identity.organizationName(),
        identity == null ? null : identity.assetCode(),
        identity == null ? null : identity.assetName(),
        identity == null ? null : identity.assetLocation(),
        identity == null ? null : identity.assetCategory(),
        drone == null ? null : drone.serialNumber(),
        drone == null ? null : drone.manufacturer(),
        drone == null ? null : drone.model(),
        inspection.getFlightPermitFileName(),
        identity == null ? List.of() : identity.inspectionItems(),
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

  private InspectionReportContextResponse reportContextForVersion(InspectionReport version) {
    Inspection inspection = inspections.findById(version.getInspectionId()).orElse(null);
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
    var sessions = fieldSessions.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
    return buildReportContextResponse(inspection, identity, drone, sessions);
  }

  private static String quote(String value) {
    if (value == null) {
      return "null";
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
  }

  private InspectionReportResponse toResponse(InspectionReport version) {
    UUID authorUserId =
        inspections
            .findById(version.getInspectionId())
            .map(Inspection::getInspectorId)
            .orElse(null);
    return toResponse(version, authorUserId);
  }

  private InspectionReportResponse toResponse(InspectionReport version, UUID authorUserId) {
    JsonNode content = readContentSnapshot(version.getContentSnapshot());
    return new InspectionReportResponse(
        version.getId(),
        version.getInspectionReportId(),
        version.getVersionNo(),
        version.getStatus(),
        authorUserId,
        version.getSubmittedAt(),
        version.getReviewerUserId(),
        version.getReviewedAt(),
        version.getReturnReason(),
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
