package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.reportcontext.ReportContextAccess;
import com.smartdroneinspection.assets.reportcontext.ReportContextAccess.Identity;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse.DroneContext;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportContextResponse.FieldSessionContext;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportAiSummaryResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionType;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.render.ReportHtmlTemplate;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.ReinspectionDefectVerificationRepository;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.InspectionSummary;
import com.smartdroneinspection.inspections.spi.ReportRenderPort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  private static final String PROMPT_VERSION = "mf3-report-v3-bilingual";

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
  private final Optional<com.smartdroneinspection.shared.api.InspectionPaymentGate> paymentGate;
  private final Optional<ReinspectionDefectVerificationRepository> defectVerificationsRepo;

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
    this(
        inspections,
        reportContextAccess,
        jdbcTemplate,
        evidence,
        findings,
        fieldSessions,
        versions,
        evidenceService,
        draftPort,
        renderPort,
        objectStore,
        userAccess,
        events,
        objectMapper,
        Optional.empty(),
        Optional.empty());
  }

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
      ObjectMapper objectMapper,
      Optional<com.smartdroneinspection.shared.api.InspectionPaymentGate> paymentGate) {
    this(
        inspections,
        reportContextAccess,
        jdbcTemplate,
        evidence,
        findings,
        fieldSessions,
        versions,
        evidenceService,
        draftPort,
        renderPort,
        objectStore,
        userAccess,
        events,
        objectMapper,
        paymentGate,
        Optional.empty());
  }

  @org.springframework.beans.factory.annotation.Autowired
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
      ObjectMapper objectMapper,
      Optional<com.smartdroneinspection.shared.api.InspectionPaymentGate> paymentGate,
      Optional<ReinspectionDefectVerificationRepository> defectVerificationsRepo) {
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
    this.paymentGate = paymentGate;
    this.defectVerificationsRepo =
        defectVerificationsRepo != null ? defectVerificationsRepo : Optional.empty();
  }

  /**
   * MF3-07/08: generate a draft from authorized sources, then let the author edit and verify it.
   */
  @Transactional
  public InspectionReportResponse generateDraft(UUID actorId, UUID inspectionId) {
    return generateDraft(actorId, inspectionId, Locale.ENGLISH, null);
  }

  @Transactional
  public InspectionReportResponse generateDraft(UUID actorId, UUID inspectionId, Locale locale) {
    return generateDraft(actorId, inspectionId, locale, null);
  }

  @Transactional
  public InspectionReportResponse generateDraft(
      UUID actorId, UUID inspectionId, Locale locale, String providedSummary) {
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

    if (latestExisting != null && latestExisting.getStatus() == ReportStatus.SUBMITTED) {
      return toResponse(latestExisting, inspection.getInspectorId());
    }

    String summaryText =
        providedSummary != null && !providedSummary.isBlank()
            ? providedSummary.trim()
            : generateAiSummaryFromContext(buildDraftContext(inspection, locale)).summary();

    int versionNo = latestExisting == null ? 1 : latestExisting.getVersionNo() + 1;
    InspectionReport version = new InspectionReport(inspectionId, versionNo, summaryText);
    InspectionReport saved = versions.saveAndFlush(version);
    inspection.markUnderReview();
    inspections.saveAndFlush(inspection);
    return toResponse(saved, inspection.getInspectorId());
  }

  /**
   * MF3-08 manual authoring. Creates an auto-generated structured draft from existing data under
   * the same review gates.
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

    if (latestExisting != null && latestExisting.getStatus() == ReportStatus.SUBMITTED) {
      return toResponse(latestExisting, inspection.getInspectorId());
    }

    String summaryText =
        buildDeterministicAiSummary(buildDraftContext(inspection, Locale.ENGLISH)).summary();

    int versionNo = latestExisting == null ? 1 : latestExisting.getVersionNo() + 1;
    InspectionReport version = new InspectionReport(inspectionId, versionNo, summaryText);
    InspectionReport saved = versions.saveAndFlush(version);
    inspection.markUnderReview();
    inspections.saveAndFlush(inspection);
    return toResponse(saved, inspection.getInspectorId());
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
      if (version.getStatus() == ReportStatus.RETURNED) {
        version.verifyAsAuthor(actorId, inspection.getInspectorId());
      }
      version.submit();
      inspection.markUnderReview();
      inspections.saveAndFlush(inspection);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }
    return toResponse(versions.saveAndFlush(version), inspection.getInspectorId());
  }

  /** A draft created before the latest findings confirmation is stale. */
  private void requireCurrentFindingsConfirmation(Inspection inspection) {
    if (!evidence.existsByInspectionIdAndIsDeletedFalse(inspection.getId())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ACTIVE_EVIDENCE_REQUIRED",
          "A report draft requires at least one active evidence item");
    }
    if (!inspection.hasFindingsConfirmation()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_NOT_CONFIRMED",
          "Confirm the current findings before authoring a report");
    }
    requireReinspectionVerificationsEvaluated(inspection);
  }

  private void requireReinspectionVerificationsEvaluated(Inspection inspection) {
    if (inspection.getInspectionType() != InspectionType.RE_INSPECTION
        || defectVerificationsRepo.isEmpty()) {
      return;
    }
    var verifications =
        defectVerificationsRepo.get().findByReinspectionIdOrderByCreatedAtAsc(inspection.getId());
    if (verifications.isEmpty()
        || !verifications.stream().allMatch(ReinspectionVerificationService::isEvaluated)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REINSPECTION_VERIFICATION_INCOMPLETE",
          "All target defects must be verified with fresh evidence or inspector field notes before authoring, submitting, or publishing the re-inspection report");
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
  public ReportPublishedResponse reviewVersion(
      UUID actorId, UUID inspectionId, UUID versionId, String reason, Locale locale) {
    return reviewVersion(actorId, inspectionId, versionId, null, reason, locale);
  }

  @Transactional
  public ReportPublishedResponse reviewVersion(
      UUID actorId,
      UUID inspectionId,
      UUID versionId,
      Boolean approve,
      String reason,
      Locale locale) {
    if (Boolean.FALSE.equals(approve)) {
      if (reason == null || reason.isBlank()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "REPORT_RETURN_REASON_REQUIRED",
            "A reviewer return requires a reason");
      }
    } else if (Boolean.TRUE.equals(approve)
        || (approve == null && (reason == null || reason.isBlank()))) {
      return publish(actorId, inspectionId, versionId, locale);
    }

    UserAccess.ActiveUser reviewer = requireQualifiedReviewer(actorId, inspectionId);
    InspectionReport version = requireVersion(inspectionId, versionId);
    try {
      version.reviewBy(reviewer.id(), false, reason);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_REVIEWER_INVALID", ex.getMessage());
    }
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    if (inspection.getStatus() == InspectionStatus.UNDER_REVIEW) {
      inspection.returnToDraft();
      inspections.saveAndFlush(inspection);
    }
    versions.saveAndFlush(version);
    return new ReportPublishedResponse(
        version.getId(),
        version.getVersionNo(),
        version.getStatus().name(),
        version.getReturnReason(),
        null,
        List.of(),
        inspection.getStatus());
  }

  /**
   * Allows a Customer (or Manager) to request clarification/revision on a PUBLISHED report before
   * commercial acceptance and payment, returning the report to RETURNED with the customer's note.
   */
  @Transactional
  public ReportPublishedResponse requestCustomerClarification(
      UUID actorId, UUID inspectionId, UUID versionId, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "CLARIFICATION_REASON_REQUIRED",
          "Please provide the clarification or revision details");
    }
    if (paymentGate.map(gate -> gate.isAcceptedOrPaid(inspectionId)).orElse(false)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REPORT_ALREADY_ACCEPTED",
          "Cannot request clarification after the report has been accepted or paid.");
    }
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    if (inspection.getClarificationCount() >= 2) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "MAX_CLARIFICATIONS_EXCEEDED",
          "Báo cáo đã qua 02 lần yêu cầu làm rõ theo quy định hợp đồng. Vui lòng liên hệ trực tiếp Quản lý nền tảng để đối soát và hòa giải.");
    }
    InspectionReport version = requireVersion(inspectionId, versionId);
    try {
      version.returnForCustomerClarification(actorId, reason);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "REPORT_CLARIFICATION_INVALID", ex.getMessage());
    }
    inspection.incrementClarificationCount();
    if (inspection.getStatus() == InspectionStatus.UNDER_REVIEW
        || inspection.getStatus() == InspectionStatus.COMPLETED) {
      inspection.returnToDraft();
    }
    inspections.saveAndFlush(inspection);
    versions.saveAndFlush(version);
    return new ReportPublishedResponse(
        version.getId(),
        version.getVersionNo(),
        version.getStatus().name(),
        version.getReturnReason(),
        null,
        List.of(),
        inspection.getStatus());
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
    if (inspection.getStatus() == InspectionStatus.UNDER_REVIEW) {
      inspection.returnToDraft();
    }
    inspection.reopenFindings();
    inspections.saveAndFlush(inspection);
    return toResponse(version, inspection.getInspectorId());
  }

  /** MF3-09/11/12: approve and publish the reviewed report. */
  @Transactional
  private ReportPublishedResponse publish(
      UUID actorId, UUID inspectionId, UUID versionId, Locale locale) {
    UserAccess.ActiveUser publisher = requireQualifiedReviewer(actorId, inspectionId);
    Inspection inspection = evidenceService.requireInspectionInScope(actorId, inspectionId);
    InspectionReport version = requireVersion(inspectionId, versionId);
    requireFreshEvidenceSnapshot(inspectionId, version);
    requireFreshFindingsSnapshot(inspectionId, version);
    requireReinspectionVerificationsEvaluated(inspection);
    try {
      version.publish(publisher.id(), inspection.getInspectorId());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", ex.getMessage());
    }

    // Record the official report publication independently from PDF export. Rendering and object
    // storage are deferred until a download is requested, so export failures cannot block review.
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

    // A published report is an immutable record of observed condition.
    inspection.markReportPublished();
    inspections.saveAndFlush(inspection);

    boolean hasConfirmedFindings =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .anyMatch(Finding::isOfficial);
    if (!hasConfirmedFindings || inspection.getInspectionType() == InspectionType.RE_INSPECTION) {
      inspection.complete();
    }
    inspections.saveAndFlush(inspection);

    return new ReportPublishedResponse(
        version.getId(),
        version.getVersionNo(),
        version.getStatus().name(),
        version.getReturnReason(),
        version.getPublishedAt(),
        List.of(),
        inspection.getStatus());
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
    UserAccess.ActiveUser actor = userAccess.findActiveUser(actorId).orElse(null);
    boolean isReviewer =
        actor != null && (actor.hasRole(Roles.MANAGER) || actor.hasRole(Roles.ADMIN));
    if (!inspection.getInspectorId().equals(actorId) && !isReviewer) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_SCOPE_DENIED",
          "Only the report author or reviewing manager may preview this draft");
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
    if (!version.isImmutable()) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND,
          "REPORT_ARTEFACT_NOT_FOUND",
          "Only published reports can be downloaded");
    }

    boolean paymentDue = paymentGate.map(gate -> gate.isPaymentDue(inspectionId)).orElse(false);
    UserAccess.ActiveUser actor = userAccess.findActiveUser(actorId).orElse(null);
    boolean isCustomer =
        actor != null
            && actor.hasRole(Roles.CUSTOMER)
            && !actor.hasRole(Roles.ADMIN)
            && !actor.hasRole(Roles.MANAGER);

    if (isCustomer && paymentDue) {
      byte[] previewContent = renderArtefact(inspectionId, version, true, Locale.ENGLISH);
      return pdfResponse(
          previewContent, "attachment", documentReference(version) + "-unpaid-preview.pdf");
    }

    String objectKey = reportObjectKey(version.getId());
    byte[] content = null;
    if (objectStore.isPresent()) {
      try (InputStream input = objectStore.get().open(objectKey)) {
        content = input.readAllBytes();
      } catch (Exception ignored) {
        // Not yet generated or cached; render on demand
      }
    }
    if (content == null || content.length == 0) {
      content = renderArtefact(inspectionId, version, false, Locale.ENGLISH);
      if (objectStore.isPresent()) {
        try (InputStream input = new ByteArrayInputStream(content)) {
          objectStore.get().put(objectKey, PDF_MEDIA_TYPE, content.length, input);
        } catch (Exception ignored) {
          // Non-blocking cache update
        }
      }
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
              : "The report PDF could not be produced; retry the download later");
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
            .find(inspection.getOwnerCustomerId(), inspection.getAssetId(), inspection.getDroneId())
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

    ReportDraftPort.SectionDraft reinspectionDraft = null;
    if (inspection.getInspectionType() == InspectionType.RE_INSPECTION
        && defectVerificationsRepo.isPresent()) {
      var verifications =
          defectVerificationsRepo.get().findByReinspectionIdOrderByCreatedAtAsc(inspectionId);
      List<ReportDraftPort.DefectVerificationSummary> dvSummaries = new ArrayList<>();
      for (var v : verifications) {
        Finding srcFinding = findings.findById(v.getSourceFindingId()).orElse(null);
        String label = srcFinding != null ? srcFinding.getDefectLabel() : "Target defect";
        String severity = srcFinding != null ? srcFinding.getSeverity().name() : "MEDIUM";
        Evidence verEv =
            v.getNewEvidenceId() != null ? evidenceById.get(v.getNewEvidenceId()) : null;
        findingViews.add(
            new ReportRenderPort.FindingView(
                v.getId().toString(),
                v.getNewEvidenceId() == null ? null : v.getNewEvidenceId().toString(),
                verEv == null ? null : verEv.getFileName(),
                label + " [" + v.getResolutionStatus().name() + "]",
                severity,
                v.getInspectorNotes(),
                srcFinding != null ? srcFinding.getRecommendedAction() : null,
                v.getResolutionStatus().name(),
                null,
                true));
        dvSummaries.add(
            new ReportDraftPort.DefectVerificationSummary(
                label, severity, v.getResolutionStatus().name(), v.getInspectorNotes()));
      }
      if ((version.getSummary() == null || version.getSummary().isBlank())
          && !dvSummaries.isEmpty()) {
        try {
          reinspectionDraft =
              createLocalDraftSynthesizer()
                  .draftSections(
                      new ReportDraftPort.DraftContext(
                          new InspectionSummary(
                              List.of(),
                              null,
                              null,
                              null,
                              null,
                              InspectionType.RE_INSPECTION.name()),
                          List.of(),
                          List.of(),
                          List.of(),
                          List.of(),
                          locale,
                          dvSummaries));
        } catch (Exception ignored) {
          // Keep null if fallback synthesis fails
        }
      }
    }

    List<ReportRenderPort.EvidenceView> evidenceViews = new ArrayList<>();
    for (Evidence item :
        evidence.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(inspectionId)) {
      byte[] content = !item.isVideo() && item.isReady() ? loadEvidenceBytes(item) : new byte[0];
      String sourceVideoName =
          item.getSourceVideoEvidenceId() != null
                  && evidenceById.containsKey(item.getSourceVideoEvidenceId())
              ? evidenceById.get(item.getSourceVideoEvidenceId()).getFileName()
              : null;
      evidenceViews.add(
          new ReportRenderPort.EvidenceView(
              item.getId().toString(),
              item.getFileName(),
              item.getContentType(),
              item.getCaptureTime() == null ? null : item.getCaptureTime().toString(),
              item.getLocationDescription(),
              item.getFieldSessionId() == null ? null : item.getFieldSessionId().toString(),
              content,
              sourceVideoName,
              item.getSourceTimestampSeconds()));
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
            version.getSummary() != null && !version.getSummary().isBlank()
                ? version.getSummary()
                : (reinspectionDraft != null ? reinspectionDraft.executiveSummary() : null),
            purposeScopeCriteria,
            null,
            null,
            reinspectionDraft != null ? reinspectionDraft.conclusionsRecommendations() : null,
            reinspectionDraft != null ? reinspectionDraft.overallCondition() : null,
            reinspectionDraft != null ? reinspectionDraft.omissionDisclosure() : null),
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
    UserAccess.ActiveUser actor = userAccess.findActiveUser(actorId).orElse(null);
    boolean isClientOnly =
        actor != null
            && actor.hasRole(Roles.CUSTOMER)
            && !actor.hasRole(Roles.MANAGER)
            && !actor.hasRole(Roles.ADMIN)
            && !actor.hasRole(Roles.INSPECTOR);

    List<InspectionReport> all = versions.findByInspectionIdOrderByVersionNoDesc(inspectionId);
    if (isClientOnly) {
      return all.stream()
          .filter(
              v ->
                  v.getStatus() == ReportStatus.PUBLISHED
                      || v.getStatus() == ReportStatus.SUPERSEDED)
          .map(this::toResponse)
          .toList();
    }
    return all.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public InspectionReportResponse getVersion(UUID actorId, UUID inspectionId, UUID versionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    InspectionReport version = requireVersion(inspectionId, versionId);
    UserAccess.ActiveUser actor = userAccess.findActiveUser(actorId).orElse(null);
    boolean isClientOnly =
        actor != null
            && actor.hasRole(Roles.CUSTOMER)
            && !actor.hasRole(Roles.MANAGER)
            && !actor.hasRole(Roles.ADMIN)
            && !actor.hasRole(Roles.INSPECTOR);

    if (isClientOnly
        && version.getStatus() != ReportStatus.PUBLISHED
        && version.getStatus() != ReportStatus.SUPERSEDED) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REPORT_NOT_PUBLISHED",
          "Only published reports are available to customer");
    }
    return toResponse(version);
  }

  private UserAccess.ActiveUser requireQualifiedReviewer(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));

    Inspection inspection;
    if (actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER)) {
      inspection = inspections.findById(inspectionId).orElseThrow(this::reportNotFound);
    } else {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "REPORT_SCOPE_DENIED", "Only a qualified Manager may review");
    }

    // Separation of duties: no report is self-reviewed.
    if (inspection.getInspectorId() != null && inspection.getInspectorId().equals(actorId)) {
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

  private BusinessException reportNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Inspection report not found");
  }

  private BusinessException inspectionNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found");
  }

  private ReportDraftPort createLocalDraftSynthesizer() {
    return new ReportDraftPort() {
      @Override
      public String modelName() {
        return "local-synthesizer";
      }

      @Override
      public SectionDraft draftSections(DraftContext context) {
        boolean isVietnamese =
            context.locale() != null && "vi".equalsIgnoreCase(context.locale().getLanguage());
        List<ReportDraftPort.FindingSummary> findings = context.findings();
        List<ReportDraftPort.DefectVerificationSummary> defectVerifications =
            context.defectVerifications() != null ? context.defectVerifications() : List.of();
        if (!defectVerifications.isEmpty()) {
          long resolvedCount =
              defectVerifications.stream()
                  .filter(v -> "RESOLVED".equalsIgnoreCase(v.resolutionStatus()))
                  .count();
          long unresolvedCount =
              defectVerifications.stream()
                  .filter(v -> "UNRESOLVED".equalsIgnoreCase(v.resolutionStatus()))
                  .count();
          boolean allResolved = resolvedCount == defectVerifications.size();
          String overallCondition = allResolved ? "GOOD" : unresolvedCount > 0 ? "POOR" : "FAIR";

          String executiveSummary;
          if (isVietnamese) {
            executiveSummary =
                allResolved
                    ? "Khảo sát bay tái kiểm định đối soát khắc phục đã hoàn tất. Đã kiểm tra và xác nhận "
                        + defectVerifications.size()
                        + " khiếm khuyết mục tiêu đã được khắc phục hoàn toàn (RESOLVED). Hiện trạng"
                        + " hạng mục đạt yêu cầu nghiệm thu."
                    : "Khảo sát bay tái kiểm định đối soát đã hoàn tất. Ghi nhận "
                        + resolvedCount
                        + " khiếm khuyết đã khắc phục và "
                        + (defectVerifications.size() - resolvedCount)
                        + " khiếm khuyết chưa đạt hoặc cần kiểm tra bổ sung. Hiện trạng"
                        + " được đánh giá ở mức "
                        + (overallCondition.equals("POOR") ? "Kém" : "Cần theo dõi")
                        + ".";
          } else {
            executiveSummary =
                allResolved
                    ? "Post-remediation aerial re-inspection completed. Confirmed "
                        + defectVerifications.size()
                        + " target defect(s) successfully resolved (RESOLVED). Inspected scope"
                        + " meets re-inspection acceptance criteria."
                    : "Post-remediation aerial re-inspection completed. Recorded "
                        + resolvedCount
                        + " resolved defect(s) and "
                        + (defectVerifications.size() - resolvedCount)
                        + " defect(s) requiring follow-up. Overall condition is assessed as "
                        + overallCondition
                        + ".";
          }

          String omissionDisclosure =
              isVietnamese
                  ? "Phạm vi bay tái kiểm định tập trung vào các vị trí khiếm khuyết mục tiêu từ"
                      + " đợt kiểm định gốc. Các cấu kiện ngoài phạm vi đối soát không được đánh"
                      + " giá lại trong phiên bay này."
                  : "Re-inspection scope was focused on the specific target defect locations from"
                      + " the source inspection. Unrelated structural elements were not"
                      + " re-evaluated during this flight.";

          String conclusionsRecommendations;
          if (isVietnamese) {
            conclusionsRecommendations =
                allResolved
                    ? "Toàn bộ các khiếm khuyết mục tiêu đã được xử lý và xác nhận đạt yêu cầu kỹ"
                        + " thuật qua ảnh đối soát trước/sau."
                    : "Các khiếm khuyết chưa đạt yêu cầu (UNRESOLVED / INCONCLUSIVE) cần tiếp tục"
                        + " được khắc phục và lập đợt tái kiểm định bổ sung.";
          } else {
            conclusionsRecommendations =
                allResolved
                    ? "All target defects have been verified as resolved against source evidence."
                        + " Suitable for customer acceptance."
                    : "Unresolved or inconclusive target defects require further remediation and"
                        + " follow-up verification prior to final sign-off.";
          }

          return new SectionDraft(
              executiveSummary,
              null,
              null,
              conclusionsRecommendations,
              overallCondition,
              omissionDisclosure);
        }

        String overallCondition;
        boolean hasCritical =
            findings.stream().anyMatch(f -> "CRITICAL".equalsIgnoreCase(f.severity()));
        boolean hasHigh = findings.stream().anyMatch(f -> "HIGH".equalsIgnoreCase(f.severity()));
        boolean hasMedium =
            findings.stream().anyMatch(f -> "MEDIUM".equalsIgnoreCase(f.severity()));

        if (hasCritical) {
          overallCondition = "CRITICAL";
        } else if (hasHigh) {
          overallCondition = "POOR";
        } else if (hasMedium) {
          overallCondition = "FAIR";
        } else {
          overallCondition = "GOOD";
        }

        String executiveSummary;
        if (isVietnamese) {
          if (findings.isEmpty()) {
            executiveSummary =
                "Khảo sát kiểm định trực quan bằng drone đã hoàn tất. Không phát hiện khiếm khuyết"
                    + " kết cấu nào trong phạm vi khảo sát. Hiện trạng tổng thể của tài sản được"
                    + " đánh giá ở mức Tốt.";
          } else {
            String defectSummary =
                findings.stream()
                    .map(f -> f.defectLabel() + " (" + f.severity() + ")")
                    .collect(Collectors.joining(", "));
            executiveSummary =
                "Khảo sát kiểm định trực quan bằng drone đã hoàn tất với "
                    + findings.size()
                    + " khiếm khuyết được ghi nhận: "
                    + defectSummary
                    + ". Hiện trạng tổng thể của tài sản được đánh giá ở mức "
                    + (overallCondition.equals("POOR")
                        ? "Kém"
                        : overallCondition.equals("FAIR") ? "Trung bình" : "Nghiêm trọng")
                    + ". Đề xuất ưu tiên các biện pháp xử lý và sửa chữa.";
          }
        } else {
          if (findings.isEmpty()) {
            executiveSummary =
                "Drone aerial visual inspection completed. No structural defects were observed"
                    + " within the inspected scope. Overall asset condition is assessed as Good.";
          } else {
            String defectSummary =
                findings.stream()
                    .map(f -> f.defectLabel() + " (" + f.severity() + ")")
                    .collect(Collectors.joining(", "));
            executiveSummary =
                "Drone aerial visual inspection completed with "
                    + findings.size()
                    + " confirmed defect(s): "
                    + defectSummary
                    + ". Overall asset condition is assessed as "
                    + overallCondition
                    + ". Priority remedial action is recommended.";
          }
        }

        String omissionDisclosure =
            isVietnamese
                ? "Phạm vi kiểm tra giới hạn ở các bề mặt ngoài quan sát được từ quỹ đạo bay an"
                    + " toàn của drone. Các cấu kiện ngầm, bên trong hộp kín và các khu vực khuất"
                    + " tầm nhìn trực tiếp không thuộc phạm vi đánh giá này."
                : "Inspection scope was limited to exterior surfaces accessible from drone safe"
                    + " flight trajectories. Subsurface elements, internal enclosed spaces, and"
                    + " areas obscured from direct line-of-sight were excluded.";

        String conclusionsRecommendations;
        if (isVietnamese) {
          if (findings.isEmpty()) {
            conclusionsRecommendations =
                "Kết cấu tài sản đảm bảo an toàn vận hành trong phạm vi kiểm định. Khuyến nghị duy"
                    + " trì lịch trình bay kiểm tra định kỳ theo quy định.";
          } else {
            String actions =
                findings.stream()
                    .map(
                        f ->
                            f.recommendedAction() != null && !f.recommendedAction().isBlank()
                                ? f.recommendedAction()
                                : f.defectLabel())
                    .collect(Collectors.joining("; "));
            conclusionsRecommendations =
                "Hiện trạng kết cấu cần được can thiệp kỹ thuật để ngăn ngừa suy giảm chất lượng."
                    + " Các biện pháp khắc phục đề xuất: "
                    + actions
                    + ". Cần đánh giá và xử lý các biện pháp khắc phục phù hợp.";
          }
        } else {
          if (findings.isEmpty()) {
            conclusionsRecommendations =
                "Asset structural condition satisfies operational requirements within the"
                    + " inspected scope. Routine periodic drone inspection schedule is"
                    + " recommended.";
          } else {
            String actions =
                findings.stream()
                    .map(
                        f ->
                            f.recommendedAction() != null && !f.recommendedAction().isBlank()
                                ? f.recommendedAction()
                                : f.defectLabel())
                    .collect(Collectors.joining("; "));
            conclusionsRecommendations =
                "Structural condition requires timely technical attention to prevent further"
                    + " deterioration. Recommended actions: "
                    + actions
                    + ".";
          }
        }

        return new SectionDraft(
            executiveSummary,
            null,
            null,
            conclusionsRecommendations,
            overallCondition,
            omissionDisclosure);
      }
    };
  }

  /** Generates or returns an executive AI summary for Manager review and Customer viewing. */
  @Transactional(readOnly = true)
  public ReportAiSummaryResponse getOrGenerateAiSummary(
      UUID actorId, UUID inspectionId, UUID versionId, Locale locale) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    InspectionReport version = requireVersion(inspectionId, versionId);
    Inspection inspection =
        inspections.findById(inspectionId).orElseThrow(this::inspectionNotFound);

    ReportDraftPort.DraftContext context = buildDraftContext(inspection, locale);
    ReportAiSummaryResponse deterministic = buildDeterministicAiSummary(context);
    if (version.getSummary() != null && !version.getSummary().isBlank()) {
      return new ReportAiSummaryResponse(
          version.getSummary(),
          deterministic.keyHighlights(),
          deterministic.recommendedActions(),
          deterministic.overallRisk());
    }

    return generateAiSummaryFromContext(context);
  }

  private ReportDraftPort.DraftContext buildDraftContext(Inspection inspection, Locale locale) {
    UUID inspectionId = inspection.getId();
    List<Evidence> confirmedEvidence =
        evidence.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(inspectionId);
    List<UUID> currentEvidenceIds = confirmedEvidence.stream().map(Evidence::getId).toList();
    List<FieldSession> recordedSessions =
        fieldSessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    Identity reportIdentity =
        reportContextAccess
            .find(inspection.getOwnerCustomerId(), inspection.getAssetId(), inspection.getDroneId())
            .orElse(null);
    List<Finding> confirmedFindings = inspectorFindings(inspectionId);

    List<Finding> detectionFindings =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(finding -> finding.getModelName() != null)
            .filter(finding -> currentEvidenceIds.contains(finding.getEvidenceId()))
            .toList();

    List<ReportDraftPort.DefectVerificationSummary> defectVerificationsList = List.of();
    if (inspection.getInspectionType() == InspectionType.RE_INSPECTION
        && defectVerificationsRepo.isPresent()) {
      defectVerificationsList =
          defectVerificationsRepo
              .get()
              .findByReinspectionIdOrderByCreatedAtAsc(inspectionId)
              .stream()
              .map(
                  v -> {
                    Finding srcFinding = findings.findById(v.getSourceFindingId()).orElse(null);
                    String label =
                        srcFinding != null ? srcFinding.getDefectLabel() : "Target defect";
                    String severity =
                        srcFinding != null ? srcFinding.getSeverity().name() : "MEDIUM";
                    return new ReportDraftPort.DefectVerificationSummary(
                        label, severity, v.getResolutionStatus().name(), v.getInspectorNotes());
                  })
              .toList();
    }

    return new ReportDraftPort.DraftContext(
        new InspectionSummary(
            reportIdentity == null ? List.of() : reportIdentity.inspectionItems(),
            inspection.getFlightPermitFileName(),
            inspection.getPlannedStartAt() == null
                ? null
                : inspection.getPlannedStartAt().toString(),
            inspection.getPlannedEndAt() == null ? null : inspection.getPlannedEndAt().toString(),
            droneDisplayReference(reportIdentity, inspection.getDroneId()),
            inspection.getInspectionType() == null
                ? "INITIAL"
                : inspection.getInspectionType().name()),
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
                        item.getCaptureTime() == null ? null : item.getCaptureTime().toString(),
                        item.getLatitude() == null ? null : item.getLatitude().toPlainString(),
                        item.getLongitude() == null ? null : item.getLongitude().toPlainString(),
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
                        session.getStartedAt() == null ? null : session.getStartedAt().toString(),
                        session.getEndedAt() == null ? null : session.getEndedAt().toString(),
                        session.getPreFlightChecklistNote(),
                        session.getAbortReason(),
                        droneDisplayReference(reportIdentity, inspection.getDroneId())))
            .toList(),
        locale,
        defectVerificationsList);
  }

  private ReportAiSummaryResponse generateAiSummaryFromContext(
      ReportDraftPort.DraftContext context) {
    if (draftPort.isPresent()) {
      try {
        ReportDraftPort.AiSummary aiSummary = draftPort.get().summarizeReport(context);
        if (aiSummary != null && aiSummary.summary() != null && !aiSummary.summary().isBlank()) {
          return new ReportAiSummaryResponse(
              aiSummary.summary(),
              aiSummary.keyHighlights() == null ? List.of() : aiSummary.keyHighlights(),
              aiSummary.recommendedActions(),
              aiSummary.overallRisk() == null ? "MEDIUM" : aiSummary.overallRisk());
        }
      } catch (Exception ex) {
        // Fall back to deterministic rule-based summary
      }
    }

    return buildDeterministicAiSummary(context);
  }

  private ReportAiSummaryResponse buildDeterministicAiSummary(
      ReportDraftPort.DraftContext context) {
    boolean isVietnamese =
        context.locale() != null && "vi".equalsIgnoreCase(context.locale().getLanguage());
    List<ReportDraftPort.DefectVerificationSummary> defectVerifications =
        context.defectVerifications() != null ? context.defectVerifications() : List.of();
    if (!defectVerifications.isEmpty()) {
      long resolvedCount =
          defectVerifications.stream()
              .filter(v -> "RESOLVED".equalsIgnoreCase(v.resolutionStatus()))
              .count();
      long unresolvedCount =
          defectVerifications.stream()
              .filter(v -> "UNRESOLVED".equalsIgnoreCase(v.resolutionStatus()))
              .count();
      boolean allResolved = resolvedCount == defectVerifications.size();
      String reinspectionRisk = allResolved ? "LOW" : (unresolvedCount > 0 ? "HIGH" : "MEDIUM");
      List<String> highlights = new ArrayList<>();
      for (var v : defectVerifications) {
        String noteSuffix =
            v.inspectorNotes() != null && !v.inspectorNotes().isBlank()
                ? " — " + v.inspectorNotes()
                : "";
        highlights.add(v.defectLabel() + " [" + v.resolutionStatus() + "]" + noteSuffix);
      }
      String summary;
      String recommendedActions;
      if (isVietnamese) {
        summary =
            allResolved
                ? "Khảo sát bay tái kiểm định đối soát khắc phục đã hoàn tất. Toàn bộ "
                    + defectVerifications.size()
                    + " khiếm khuyết mục tiêu đã được xác nhận khắc phục đạt chuẩn (RESOLVED)."
                : "Khảo sát bay tái kiểm định đối soát ghi nhận "
                    + resolvedCount
                    + "/"
                    + defectVerifications.size()
                    + " khiếm khuyết đã khắc phục (RESOLVED) và "
                    + (defectVerifications.size() - resolvedCount)
                    + " khiếm khuyết chưa đạt hoặc cần kiểm tra bổ sung.";
        recommendedActions =
            allResolved
                ? "Khuyến nghị Khách hàng xác nhận nghiệm thu báo cáo tái kiểm định."
                : "Khuyến nghị tiếp tục khắc phục các khiếm khuyết chưa đạt và lập đợt tái kiểm định bổ sung.";
      } else {
        summary =
            allResolved
                ? "Post-remediation aerial re-inspection completed. All "
                    + defectVerifications.size()
                    + " target defect(s) are verified as RESOLVED."
                : "Post-remediation aerial re-inspection recorded "
                    + resolvedCount
                    + "/"
                    + defectVerifications.size()
                    + " resolved defect(s) and "
                    + (defectVerifications.size() - resolvedCount)
                    + " unresolved/inconclusive item(s).";
        recommendedActions =
            allResolved
                ? "Recommend Customer acceptance of the re-inspection deliverable."
                : "Recommend further remediation for unresolved target defects followed by verification.";
      }
      return new ReportAiSummaryResponse(summary, highlights, recommendedActions, reinspectionRisk);
    }

    List<ReportDraftPort.FindingSummary> findings = context.findings();
    boolean hasCritical =
        findings.stream().anyMatch(f -> "CRITICAL".equalsIgnoreCase(f.severity()));
    boolean hasHigh = findings.stream().anyMatch(f -> "HIGH".equalsIgnoreCase(f.severity()));
    boolean hasMedium = findings.stream().anyMatch(f -> "MEDIUM".equalsIgnoreCase(f.severity()));

    String overallRisk = hasCritical ? "CRITICAL" : hasHigh ? "HIGH" : hasMedium ? "MEDIUM" : "LOW";

    String summary;
    List<String> keyHighlights = new ArrayList<>();
    String recommendedActions;

    if (isVietnamese) {
      if (findings.isEmpty()) {
        summary =
            "Khảo sát kiểm định trực quan bằng drone đã hoàn tất. Không phát hiện khiếm khuyết kết"
                + " cấu nào trong phạm vi khảo sát. Hiện trạng tổng thể của tài sản được đánh giá ở"
                + " mức Tốt.";
        keyHighlights.add("Không ghi nhận khiếm khuyết kết cấu nào");
        recommendedActions = "Khuyến nghị duy trì lịch trình bay kiểm tra định kỳ theo quy định.";
      } else {
        long criticalCount =
            findings.stream().filter(f -> "CRITICAL".equalsIgnoreCase(f.severity())).count();
        long highCount =
            findings.stream().filter(f -> "HIGH".equalsIgnoreCase(f.severity())).count();
        long mediumCount =
            findings.stream().filter(f -> "MEDIUM".equalsIgnoreCase(f.severity())).count();
        long lowCount = findings.stream().filter(f -> "LOW".equalsIgnoreCase(f.severity())).count();

        summary =
            String.format(
                "Khảo sát kiểm định trực quan bằng drone đã hoàn tất với %d khiếm khuyết được ghi"
                    + " nhận. Đánh giá rủi ro hiện trạng ở mức %s.",
                findings.size(), overallRisk);

        if (criticalCount > 0)
          keyHighlights.add(criticalCount + " khiếm khuyết mức độ Nghiêm trọng (CRITICAL)");
        if (highCount > 0) keyHighlights.add(highCount + " khiếm khuyết mức độ Cao (HIGH)");
        if (mediumCount > 0)
          keyHighlights.add(mediumCount + " khiếm khuyết mức độ Trung bình (MEDIUM)");
        if (lowCount > 0) keyHighlights.add(lowCount + " khiếm khuyết mức độ Thấp (LOW)");

        recommendedActions =
            "Đề xuất đánh giá các khiếm khuyết nghiêm trọng và lập kế hoạch theo dõi phù hợp.";
      }
    } else {
      if (findings.isEmpty()) {
        summary =
            "Drone aerial visual inspection completed. No structural defects were observed within the"
                + " inspected scope. Overall asset condition is assessed as Good.";
        keyHighlights.add("No structural defects observed");
        recommendedActions = "Routine periodic drone inspection schedule is recommended.";
      } else {
        summary =
            String.format(
                "Drone aerial visual inspection completed with %d confirmed defect(s). Overall"
                    + " condition risk is assessed as %s.",
                findings.size(), overallRisk);

        findings.stream()
            .filter(
                f ->
                    "CRITICAL".equalsIgnoreCase(f.severity())
                        || "HIGH".equalsIgnoreCase(f.severity()))
            .forEach(f -> keyHighlights.add(f.defectLabel() + " (" + f.severity() + ")"));
        if (keyHighlights.isEmpty()) {
          keyHighlights.add(findings.size() + " defect(s) confirmed at moderate severity");
        }

        recommendedActions =
            "Prompt evaluation and appropriate follow-up are recommended for critical/high defects.";
      }
    }

    return new ReportAiSummaryResponse(summary, keyHighlights, recommendedActions, overallRisk);
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
        inspection.getFindingsConfirmedAt());
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
            .find(inspection.getOwnerCustomerId(), inspection.getAssetId(), inspection.getDroneId())
            .orElse(null);
    var drone =
        identity == null || identity.droneSerialNumber() == null
            ? null
            : new DroneContext(
                identity.droneSerialNumber(), identity.droneManufacturer(), identity.droneModel());
    var sessions = fieldSessions.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
    return buildReportContextResponse(inspection, identity, drone, sessions);
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
    String summaryText = version.getSummary();
    if (summaryText == null || summaryText.isBlank()) {
      Inspection inspection = inspections.findById(version.getInspectionId()).orElse(null);
      if (inspection != null) {
        summaryText =
            buildDeterministicAiSummary(buildDraftContext(inspection, Locale.ENGLISH)).summary();
      }
    }
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
        summaryText,
        version.isClarificationRequested(),
        reportContextForVersion(version));
  }

  private static String droneDisplayReference(Identity identity, UUID droneId) {
    if (identity != null && identity.droneModel() != null && !identity.droneModel().isBlank()) {
      StringBuilder sb = new StringBuilder();
      if (identity.droneManufacturer() != null && !identity.droneManufacturer().isBlank()) {
        sb.append(identity.droneManufacturer().trim()).append(" ");
      }
      sb.append(identity.droneModel().trim());
      if (identity.droneSerialNumber() != null && !identity.droneSerialNumber().isBlank()) {
        sb.append(" (S/N: ").append(identity.droneSerialNumber().trim()).append(")");
      }
      return sb.toString();
    }
    return droneId == null ? null : droneId.toString();
  }
}
