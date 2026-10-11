package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.ReportSectionsRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportVersionResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportSectionsResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.InspectionReportVersion;
import com.smartdroneinspection.inspections.domain.ReportSections;
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

  private static final String PROMPT_VERSION = "mf3-report-v1";

  /** Recorded as the version model when the Inspector authors the draft without automated help. */
  private static final String MANUAL_MODEL_NAME = "manual-authoring";

  private static final String PDF_MEDIA_TYPE = "application/pdf";

  private final InspectionRepository inspections;
  private final EvidenceRepository evidence;
  private final VerifiedFindingRepository findings;
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
    this.findings = findings;
    this.reports = reports;
    this.versions = versions;
    this.evidenceService = evidenceService;
    this.qualityService = qualityService;
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

    // MF3-11: the PDF is the version's deliverable, so it is rendered and stored before the version
    // can be marked published. A failure here rolls the publish back rather than leaving a
    // published
    // version with no artefact.
    storePublishedArtefact(inspectionId, version);
    versions.saveAndFlush(version);
    report.setStatus(ReportStatus.PUBLISHED);
    reports.saveAndFlush(report);

    List<UUID> repairRequired =
        findings.findRepairRequired(inspectionId).stream().map(VerifiedFinding::getId).toList();
    // The inspection state rules live on the entity, so an out-of-order publish is refused the same
    // way whether it arrives over HTTP, a batch job or a future endpoint. The version-level check
    // above is translated for the same reason.
    try {
      inspection.markReportPublished();
      if (repairRequired.isEmpty()) {
        // A no-repair outcome closes with its limitations preserved, never an empty work order.
        inspection.complete();
      } else {
        inspection.markRepairPending();
      }
    } catch (IllegalStateException ex) {
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
    Map<UUID, String> findingLabelByEvidence = new LinkedHashMap<>();
    if (!preview) {
      List<VerifiedFinding> confirmed =
          findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
              .filter(VerifiedFinding::isOfficial)
              .toList();
      for (VerifiedFinding finding : confirmed) {
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
                finding.getDecision().name(),
                displayName(finding.getDecidedByUserId()),
                finding.getDecidedAt() == null ? null : finding.getDecidedAt().toString(),
                finding.getMeasurement(),
                finding.isRepairRequired() ? "Yes" : "No",
                finding.getEvidenceId() == null
                    ? List.of()
                    : List.of(finding.getEvidenceId().toString())));
        if (finding.getEvidenceId() != null) {
          findingLabelByEvidence.put(finding.getEvidenceId(), finding.getDefectLabel());
        }
      }
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
              findingLabelByEvidence.get(item.getId()),
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
