package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.api.dto.request.ClientReportDecisionRequest;
import com.smartdroneinspection.inspections.api.dto.request.PeerReviewDecisionRequest;
import com.smartdroneinspection.inspections.api.dto.response.ReportSnapshot;
import com.smartdroneinspection.inspections.api.dto.response.ReportVersionResponse;
import com.smartdroneinspection.inspections.domain.ChecklistResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.PeerReview;
import com.smartdroneinspection.inspections.domain.ReportVersion;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.PeerReviewDecision;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.events.ReportAcceptedEvent;
import com.smartdroneinspection.inspections.repository.ChecklistResponseRepository;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.PeerReviewRepository;
import com.smartdroneinspection.inspections.repository.ReportVersionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.spi.EvidenceObjectStore;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class InspectionReportService {

  private static final List<ReportStatus> CLIENT_VISIBLE_VERSION_STATUSES =
      List.of(ReportStatus.RELEASED, ReportStatus.REVISION_REQUESTED, ReportStatus.ACCEPTED);

  private final InspectionRepository inspections;
  private final InspectionAssignmentRepository assignments;
  private final InspectionReportRepository reports;
  private final ReportVersionRepository versions;
  private final PeerReviewRepository reviews;
  private final ChecklistTemplateRepository checklistTemplates;
  private final ChecklistResponseRepository checklistResponses;
  private final EvidenceRepository evidence;
  private final VerifiedFindingRepository findings;
  private final InspectionServiceOrderRepository serviceOrders;
  private final InspectionRequestRepository inspectionRequests;
  private final UserAccess users;
  private final Optional<EvidenceObjectStore> objectStore;
  private final Optional<ReportDraftPort> reportDraftPort;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher events;
  private final int maxNarrativeChars;

  public InspectionReportService(
      InspectionRepository inspections,
      InspectionAssignmentRepository assignments,
      InspectionReportRepository reports,
      ReportVersionRepository versions,
      PeerReviewRepository reviews,
      ChecklistTemplateRepository checklistTemplates,
      ChecklistResponseRepository checklistResponses,
      EvidenceRepository evidence,
      VerifiedFindingRepository findings,
      InspectionServiceOrderRepository serviceOrders,
      InspectionRequestRepository inspectionRequests,
      UserAccess users,
      Optional<EvidenceObjectStore> objectStore,
      Optional<ReportDraftPort> reportDraftPort,
      ObjectMapper objectMapper,
      ApplicationEventPublisher events,
      @Value("${app.report.narrative-max-chars:10000}") int maxNarrativeChars) {
    this.inspections = inspections;
    this.assignments = assignments;
    this.reports = reports;
    this.versions = versions;
    this.reviews = reviews;
    this.checklistTemplates = checklistTemplates;
    this.checklistResponses = checklistResponses;
    this.evidence = evidence;
    this.findings = findings;
    this.serviceOrders = serviceOrders;
    this.inspectionRequests = inspectionRequests;
    this.users = users;
    this.objectStore = objectStore;
    this.reportDraftPort = reportDraftPort;
    this.objectMapper = objectMapper;
    this.events = events;
    this.maxNarrativeChars = maxNarrativeChars;
  }

  @Transactional(readOnly = true)
  public List<ReportVersionResponse> listReports(UUID actorId) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    List<InspectionReport> visible;
    if (actor.hasRole(Roles.SERVICE_MANAGER)) {
      visible = reports.findAllByOrderByUpdatedAtDesc();
    } else if (actor.hasRole(Roles.INSPECTOR)) {
      Map<UUID, InspectionReport> unique = new LinkedHashMap<>();
      reports
          .findByAuthorUserIdOrderByUpdatedAtDesc(actorId)
          .forEach(report -> unique.put(report.getId(), report));
      reviews.findByReviewerUserIdOrderByAssignedAtDesc(actorId).stream()
          .map(review -> versions.findById(review.getReportVersionId()).orElse(null))
          .filter(version -> version != null)
          .map(version -> reports.findById(version.getReportId()).orElse(null))
          .filter(report -> report != null)
          .forEach(report -> unique.putIfAbsent(report.getId(), report));
      visible = new ArrayList<>(unique.values());
    } else if (actor.hasRole(Roles.CLIENT) && actor.organizationId() != null) {
      visible =
          reports.findVisibleToOrganization(
              actor.organizationId(),
              List.of(
                  ReportStatus.RELEASED, ReportStatus.ACCEPTED, ReportStatus.REVISION_REQUESTED));
    } else {
      throw reportScopeDenied();
    }

    return visible.stream()
        .map(
            report ->
                versionForViewer(report, actor)
                    .map(version -> toResponse(report, version, actor))
                    .orElse(null))
        .filter(response -> response != null)
        .toList();
  }

  @Transactional(readOnly = true)
  public ReportVersionResponse getReport(UUID actorId, UUID reportId) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    InspectionReport report = reports.findById(reportId).orElseThrow(this::reportNotFound);
    requireReportAccess(actor, report);
    ReportVersion version = versionForViewer(report, actor).orElseThrow(this::reportNotFound);
    return toResponse(report, version, actor);
  }

  @Transactional(readOnly = true)
  public ReportVersionResponse getInspectionReport(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActiveRole(actorId, Roles.INSPECTOR);
    InspectionReport report =
        reports.findByInspectionId(inspectionId).orElseThrow(this::reportNotFound);
    requireReportAccess(actor, report);
    ReportVersion version = versionForViewer(report, actor).orElseThrow(this::reportNotFound);
    return toResponse(report, version, actor);
  }

  @Transactional
  public ReportVersionResponse createDraft(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActiveRole(actorId, Roles.INSPECTOR);
    Inspection inspection = requireAssignedInspection(actor, inspectionId, true);
    Optional<InspectionReport> existing = reports.findByInspectionId(inspectionId);
    if (existing.isPresent()) {
      InspectionReport report = existing.get();
      if (!report.getAuthorUserId().equals(actorId)) {
        throw reportScopeDenied();
      }
      ReportVersion current = requireCurrentVersion(report, false);
      return toResponse(report, current, actor);
    }

    ReportSnapshot snapshot = composeSnapshot(inspection);
    InspectionReport report = reports.saveAndFlush(new InspectionReport(inspectionId, actorId));
    ReportVersion version =
        versions.saveAndFlush(
            new ReportVersion(report.getId(), 1, null, actorId, serialize(snapshot)));
    report.startVersion(1);
    reports.saveAndFlush(report);
    return toResponse(report, version, actor);
  }

  @Transactional
  public ReportVersionResponse createRevision(UUID actorId, UUID reportId) {
    UserAccess.ActiveUser actor = requireActiveRole(actorId, Roles.INSPECTOR);
    InspectionReport report = requireReport(reportId, true);
    if (!report.getAuthorUserId().equals(actorId)) {
      throw reportScopeDenied();
    }
    if (report.getStatus() != ReportStatus.CHANGES_REQUESTED
        && report.getStatus() != ReportStatus.REVISION_REQUESTED) {
      throw reportStateConflict("A revision can only be created after a change request.");
    }
    Inspection inspection = requireAssignedInspection(actor, report.getInspectionId(), true);
    ReportVersion source = requireCurrentVersion(report, true);
    if (source.getStatus() == ReportStatus.ACCEPTED || source.isImmutable()) {
      throw reportStateConflict("Accepted report versions cannot be changed.");
    }
    ReportVersion revision =
        versions.saveAndFlush(
            new ReportVersion(
                report.getId(),
                report.getCurrentVersionNumber() + 1,
                source.getId(),
                actorId,
                serialize(composeSnapshot(inspection))));
    report.startVersion(revision.getVersionNumber());
    reports.saveAndFlush(report);
    return toResponse(report, revision, actor);
  }

  @Transactional
  public ReportVersionResponse assignReviewer(
      UUID actorId, UUID reportId, UUID versionId, UUID reviewerId) {
    UserAccess.ActiveUser manager = requireActiveRole(actorId, Roles.SERVICE_MANAGER);
    InspectionReport report = requireReport(reportId, true);
    ReportVersion version = requireCurrentVersion(report, versionId, true);
    if (version.getStatus() != ReportStatus.DRAFT) {
      throw reportStateConflict("A reviewer can only be assigned to a draft version.");
    }
    if (reviewerId.equals(report.getAuthorUserId())) {
      throw invalidReviewer("The report author cannot review their own report.");
    }
    UserAccess.ActiveUser reviewer =
        users
            .findActiveUser(reviewerId)
            .filter(user -> user.hasRole(Roles.INSPECTOR))
            .orElseThrow(() -> invalidReviewer("The reviewer must be an active Inspector."));
    if (reviewer.id().equals(report.getAuthorUserId())) {
      throw invalidReviewer("The report author cannot review their own report.");
    }
    if (reviews.findByReportVersionId(versionId).isPresent()) {
      throw reportStateConflict("A reviewer has already been assigned to this version.");
    }
    reviews.saveAndFlush(new PeerReview(versionId, reviewer.id(), manager.id(), Instant.now()));
    return toResponse(report, version, manager);
  }

  @Transactional
  public ReportVersionResponse submitForReview(UUID actorId, UUID reportId, UUID versionId) {
    UserAccess.ActiveUser actor = requireActiveRole(actorId, Roles.INSPECTOR);
    InspectionReport report = requireReport(reportId, true);
    ReportVersion version = requireCurrentVersion(report, versionId, true);
    if (!report.getAuthorUserId().equals(actorId)) {
      throw reportScopeDenied();
    }
    if (version.getStatus() != ReportStatus.DRAFT) {
      throw reportStateConflict("Only a draft version can be submitted for peer review.");
    }
    if (reviews
        .findByReportVersionId(versionId)
        .filter(review -> review.getDecision() == PeerReviewDecision.PENDING)
        .isEmpty()) {
      throw reportStateConflict("Assign a peer reviewer before submitting the report.");
    }
    ensureComplete(version);
    version.submitForReview();
    report.changeStatus(ReportStatus.AWAITING_PEER_REVIEW);
    versions.saveAndFlush(version);
    reports.saveAndFlush(report);
    return toResponse(report, version, actor);
  }

  @Transactional
  public ReportVersionResponse review(
      UUID actorId, UUID reportId, UUID versionId, PeerReviewDecisionRequest request) {
    UserAccess.ActiveUser actor = requireActiveRole(actorId, Roles.INSPECTOR);
    InspectionReport report = requireReport(reportId, true);
    ReportVersion version = requireCurrentVersion(report, versionId, true);
    if (report.getAuthorUserId().equals(actorId)) {
      throw reportScopeDenied();
    }
    if (request == null || request.decision() == null) {
      throw reportStateConflict("A peer-review decision is required.");
    }
    PeerReview review =
        reviews
            .findForUpdateByReportVersionId(versionId)
            .filter(value -> value.getReviewerUserId().equals(actorId))
            .orElseThrow(this::reportScopeDenied);
    if (version.getStatus() != ReportStatus.AWAITING_PEER_REVIEW
        || review.getDecision() != PeerReviewDecision.PENDING) {
      throw reportStateConflict("This report version is not awaiting this review decision.");
    }
    PeerReviewDecision decision =
        request.decision() == PeerReviewDecisionRequest.Decision.APPROVED
            ? PeerReviewDecision.APPROVED
            : PeerReviewDecision.CHANGES_REQUESTED;
    if (decision == PeerReviewDecision.CHANGES_REQUESTED
        && (request.comments() == null || request.comments().isBlank())) {
      throw reportStateConflict("A request for changes must include comments.");
    }
    review.decide(decision, request.comments());
    if (decision == PeerReviewDecision.APPROVED) {
      version.approve();
      report.changeStatus(ReportStatus.TECHNICALLY_APPROVED);
    } else {
      version.requestChanges();
      report.changeStatus(ReportStatus.CHANGES_REQUESTED);
    }
    reviews.saveAndFlush(review);
    versions.saveAndFlush(version);
    reports.saveAndFlush(report);
    return toResponse(report, version, actor);
  }

  @Transactional
  public ReportVersionResponse release(UUID actorId, UUID reportId, UUID versionId) {
    UserAccess.ActiveUser manager = requireActiveRole(actorId, Roles.SERVICE_MANAGER);
    InspectionReport report = requireReport(reportId, true);
    ReportVersion version = requireCurrentVersion(report, versionId, true);
    PeerReview review =
        reviews
            .findByReportVersionId(versionId)
            .filter(value -> value.getDecision() == PeerReviewDecision.APPROVED)
            .orElseThrow(
                () -> reportStateConflict("Only a technically approved version can be released."));
    if (review.getReviewerUserId().equals(report.getAuthorUserId())) {
      throw reportStateConflict("A report author cannot approve their own version.");
    }
    ensureComplete(version);
    version.release();
    report.changeStatus(ReportStatus.RELEASED);
    versions.saveAndFlush(version);
    reports.saveAndFlush(report);
    return toResponse(report, version, manager);
  }

  @Transactional
  public ReportVersionResponse clientDecision(
      UUID actorId, UUID reportId, UUID versionId, ClientReportDecisionRequest request) {
    UserAccess.ActiveUser client = requireActiveRole(actorId, Roles.CLIENT);
    InspectionReport report = requireReport(reportId, true);
    requireClientOrganization(client, report);
    ReportVersion version = requireCurrentVersion(report, versionId, true);
    if (version.getStatus() == ReportStatus.ACCEPTED
        && report.getStatus() == ReportStatus.ACCEPTED) {
      completeInspection(report);
      return toResponse(report, version, client);
    }
    if (request == null || request.decision() == null) {
      throw reportStateConflict("A Client report decision is required.");
    }
    if (version.getStatus() != ReportStatus.RELEASED) {
      throw reportStateConflict("Only a released report version can receive a Client decision.");
    }
    if (request.decision() == ClientReportDecisionRequest.Decision.ACCEPT) {
      version.accept(actorId);
      report.changeStatus(ReportStatus.ACCEPTED);
      versions.saveAndFlush(version);
      reports.saveAndFlush(report);

      completeInspection(report);

      events.publishEvent(
          new ReportAcceptedEvent(
              report.getId(),
              version.getId(),
              report.getInspectionId(),
              client.organizationId(),
              versionSnapshot(version).assetId(),
              actorId,
              version.getAcceptedAt()));
    } else {
      if (request.reason() == null || request.reason().isBlank()) {
        throw reportStateConflict("A revision request must include a reason.");
      }
      version.requestClientRevision(actorId, request.reason());
      report.changeStatus(ReportStatus.REVISION_REQUESTED);
      versions.saveAndFlush(version);
      reports.saveAndFlush(report);
    }
    return toResponse(report, version, client);
  }

  @Transactional(readOnly = true)
  public EvidenceContent openReleasedEvidence(
      UUID actorId, UUID reportId, UUID versionId, UUID evidenceId) {
    UserAccess.ActiveUser client = requireActiveRole(actorId, Roles.CLIENT);
    InspectionReport report = reports.findById(reportId).orElseThrow(this::reportNotFound);
    requireClientOrganization(client, report);
    ReportVersion version =
        versions
            .findById(versionId)
            .filter(value -> value.getReportId().equals(reportId))
            .filter(value -> CLIENT_VISIBLE_VERSION_STATUSES.contains(value.getStatus()))
            .orElseThrow(this::reportNotFound);
    ReportSnapshot snapshot = versionSnapshot(version);
    if (snapshot.evidence().stream().noneMatch(item -> item.id().equals(evidenceId))) {
      throw reportNotFound();
    }
    Evidence source =
        evidence
            .findByIdAndInspectionIdAndUploadStatus(
                evidenceId, report.getInspectionId(), UploadStatus.AVAILABLE)
            .orElseThrow(this::reportNotFound);
    EvidenceObjectStore store = objectStore.orElseThrow(this::storageUnavailable);
    try {
      return new EvidenceContent(
          store.open(source.getObjectKey()),
          source.getFileName(),
          source.getContentType(),
          source.getSizeBytes());
    } catch (IOException exception) {
      throw storageUnavailable();
    }
  }

  @Transactional
  public ReportVersionResponse generateAiDraft(UUID actorId, UUID reportId, UUID versionId) {
    UserAccess.ActiveUser actor = requireActiveRole(actorId, Roles.INSPECTOR);
    InspectionReport report = requireReport(reportId, true);
    ReportVersion version = requireCurrentVersion(report, versionId, true);
    if (!report.getAuthorUserId().equals(actorId)) {
      throw reportScopeDenied();
    }
    if (version.getStatus() != ReportStatus.DRAFT) {
      throw reportStateConflict("AI drafts can only be generated for draft versions.");
    }
    ReportDraftPort port =
        reportDraftPort.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "REPORT_DRAFT_UNAVAILABLE",
                    "Report draft generation is not configured."));

    ReportSnapshot snapshot = versionSnapshot(version);
    ReportDraftPort.DraftContext context =
        new ReportDraftPort.DraftContext(
            snapshot.checklist().stream()
                .map(
                    item ->
                        new ReportDraftPort.ChecklistEntrySummary(
                            item.prompt(), item.responseValue(), item.notes()))
                .toList(),
            snapshot.findings().stream()
                .map(
                    finding ->
                        new ReportDraftPort.FindingSummary(
                            finding.defectLabel(),
                            finding.severity().name(),
                            finding.technicalNotes()))
                .toList(),
            snapshot.evidence().stream()
                .map(
                    item ->
                        new ReportDraftPort.EvidenceSummary(item.fileName(), item.contentType()))
                .toList());

    String narrative;
    try {
      narrative = port.generateDraft(context);
    } catch (IOException exception) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REPORT_DRAFT_UNAVAILABLE",
          "Report draft generation failed. Please try again.");
    }

    if (narrative == null || narrative.isBlank()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REPORT_DRAFT_UNAVAILABLE",
          "Generated narrative is blank.");
    }
    if (narrative.length() > maxNarrativeChars) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "REPORT_DRAFT_INVALID",
          "Generated narrative cannot exceed " + maxNarrativeChars + " characters.");
    }

    ReportSnapshot updatedSnapshot = snapshot.withAiDraftProvenance(narrative, port.modelName());
    version = updateVersionSnapshot(version, updatedSnapshot);
    versions.saveAndFlush(version);
    return toResponse(report, version, actor);
  }

  @Transactional
  public ReportVersionResponse updateNarrative(
      UUID actorId, UUID reportId, UUID versionId, String narrative) {
    UserAccess.ActiveUser actor = requireActiveRole(actorId, Roles.INSPECTOR);
    InspectionReport report = requireReport(reportId, true);
    ReportVersion version = requireCurrentVersion(report, versionId, true);
    if (!report.getAuthorUserId().equals(actorId)) {
      throw reportScopeDenied();
    }
    if (version.getStatus() != ReportStatus.DRAFT) {
      throw reportStateConflict("Narrative can only be updated for draft versions.");
    }
    if (narrative == null || narrative.isBlank() || narrative.length() > maxNarrativeChars) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "REPORT_DRAFT_INVALID",
          "Narrative text is required and cannot exceed " + maxNarrativeChars + " characters.");
    }

    ReportSnapshot snapshot = versionSnapshot(version);
    ReportSnapshot updatedSnapshot = snapshot.withAiDraftNarrative(narrative.trim());
    version = updateVersionSnapshot(version, updatedSnapshot);
    versions.saveAndFlush(version);
    return toResponse(report, version, actor);
  }

  private ReportVersion updateVersionSnapshot(ReportVersion version, ReportSnapshot snapshot) {
    version.updateContentSnapshot(serialize(snapshot));
    return version;
  }

  private Optional<ReportVersion> versionForViewer(
      InspectionReport report, UserAccess.ActiveUser actor) {
    if (actor.hasRole(Roles.CLIENT)) {
      if (actor.organizationId() == null || !clientOwnsReport(actor, report)) {
        return Optional.empty();
      }
      return versions.findFirstByReportIdAndStatusInOrderByVersionNumberDesc(
          report.getId(), CLIENT_VISIBLE_VERSION_STATUSES);
    }
    return versions.findByReportIdOrderByVersionNumberDesc(report.getId()).stream().findFirst();
  }

  private void requireReportAccess(UserAccess.ActiveUser actor, InspectionReport report) {
    if (actor.hasRole(Roles.SERVICE_MANAGER)) {
      return;
    }
    if (actor.hasRole(Roles.INSPECTOR)) {
      if (report.getAuthorUserId().equals(actor.id())) {
        return;
      }
      boolean assignedReviewer =
          versions.findByReportIdOrderByVersionNumberDesc(report.getId()).stream()
              .map(version -> reviews.findByReportVersionId(version.getId()).orElse(null))
              .filter(review -> review != null)
              .anyMatch(review -> review.getReviewerUserId().equals(actor.id()));
      if (assignedReviewer) {
        return;
      }
    }
    if (actor.hasRole(Roles.CLIENT)
        && actor.organizationId() != null
        && clientOwnsReport(actor, report)) {
      return;
    }
    throw reportScopeDenied();
  }

  private boolean clientOwnsReport(UserAccess.ActiveUser client, InspectionReport report) {
    if (client.organizationId() == null) {
      return false;
    }
    Inspection inspection = inspections.findById(report.getInspectionId()).orElse(null);
    if (inspection == null) {
      return false;
    }
    InspectionServiceOrder order =
        serviceOrders.findById(inspection.getServiceOrderId()).orElse(null);
    if (order == null) {
      return false;
    }
    InspectionRequest request =
        inspectionRequests.findById(order.getInspectionRequestId()).orElse(null);
    return request != null && request.getOrganizationId().equals(client.organizationId());
  }

  private void requireClientOrganization(UserAccess.ActiveUser client, InspectionReport report) {
    if (!clientOwnsReport(client, report)) {
      throw reportScopeDenied();
    }
  }

  private ReportSnapshot composeSnapshot(Inspection inspection) {
    ChecklistTemplate template =
        checklistTemplates
            .findDetailedById(inspection.getChecklistTemplateId())
            .orElseThrow(this::reportNotFound);
    Map<UUID, ChecklistResponse> responsesByItem =
        checklistResponses.findByInspectionId(inspection.getId()).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    ChecklistResponse::getChecklistItemId, response -> response));
    List<ReportSnapshot.ChecklistEntry> checklist =
        template.getItems().stream()
            .map(
                item -> {
                  ChecklistResponse response = responsesByItem.get(item.getId());
                  return new ReportSnapshot.ChecklistEntry(
                      item.getId(),
                      item.getItemCode(),
                      item.getPrompt(),
                      item.isRequired(),
                      response == null ? null : response.getResponseValue(),
                      response == null ? null : response.getNotes(),
                      response == null ? null : response.getCompletedAt());
                })
            .toList();
    if (checklist.stream().anyMatch(item -> item.required() && item.responseValue() == null)) {
      throw reportStateConflict("Complete all required checklist items before compiling a report.");
    }

    List<Evidence> availableEvidence =
        evidence.findByInspectionIdAndUploadStatusOrderByCreatedAtDesc(
            inspection.getId(), UploadStatus.AVAILABLE);
    if (availableEvidence.isEmpty()) {
      throw reportStateConflict("At least one available evidence item is required in the report.");
    }
    List<ReportSnapshot.EvidenceEntry> evidenceEntries =
        availableEvidence.stream()
            .map(
                item ->
                    new ReportSnapshot.EvidenceEntry(
                        item.getId(),
                        item.getFileName(),
                        item.getContentType(),
                        item.getSizeBytes(),
                        item.getChecksumSha256(),
                        item.getSource().name(),
                        item.getCaptureTime(),
                        item.getLatitude(),
                        item.getLongitude()))
            .toList();
    List<ReportSnapshot.FindingEntry> findingEntries =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspection.getId()).stream()
            .map(this::toFindingEntry)
            .toList();
    return new ReportSnapshot(
        inspection.getId(),
        inspection.getServiceOrderId(),
        inspection.getAssetId(),
        inspection.getChecklistTemplateId(),
        template.getName(),
        Instant.now(),
        checklist,
        evidenceEntries,
        findingEntries,
        null,
        null);
  }

  private ReportSnapshot.FindingEntry toFindingEntry(VerifiedFinding finding) {
    return new ReportSnapshot.FindingEntry(
        finding.getId(),
        finding.getFindingCode(),
        finding.getEvidenceId(),
        finding.getSource(),
        finding.getDefectLabel(),
        finding.getSeverity(),
        finding.getLocationDescription(),
        finding.getTechnicalNotes(),
        finding.getRecommendedAction(),
        finding.getBoundingBox());
  }

  private void ensureComplete(ReportVersion version) {
    ReportSnapshot snapshot = versionSnapshot(version);
    if (snapshot.evidence().isEmpty()
        || snapshot.checklist().stream()
            .anyMatch(item -> item.required() && item.responseValue() == null)) {
      throw reportStateConflict("This report version is missing required checklist or evidence.");
    }
  }

  private ReportSnapshot versionSnapshot(ReportVersion version) {
    try {
      return objectMapper.readValue(version.getContentSnapshot(), ReportSnapshot.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored report snapshot is invalid JSON", exception);
    }
  }

  private String serialize(ReportSnapshot snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Report snapshot could not be serialized", exception);
    }
  }

  private ReportVersionResponse toResponse(
      InspectionReport report, ReportVersion version, UserAccess.ActiveUser actor) {
    Optional<PeerReview> review = reviews.findByReportVersionId(version.getId());
    ReportVersionResponse.ReviewSummary reviewSummary =
        review
            .map(
                value ->
                    new ReportVersionResponse.ReviewSummary(
                        value.getReviewerUserId(),
                        value.getDecision(),
                        actor.hasRole(Roles.CLIENT) ? null : value.getComments(),
                        value.getReviewedAt()))
            .orElse(null);
    return new ReportVersionResponse(
        report.getId(),
        version.getId(),
        version.getVersionNumber(),
        report.getInspectionId(),
        report.getAuthorUserId(),
        version.getSourceVersionId(),
        report.getStatus(),
        version.getStatus(),
        versionSnapshot(version),
        actor.hasRole(Roles.CLIENT) ? null : reviewSummary,
        version.getCreatedAt(),
        version.getReleasedAt(),
        version.getAcceptedAt(),
        version.getClientDecisionByUserId(),
        version.getClientDecisionReason());
  }

  private ReportVersion requireCurrentVersion(InspectionReport report, boolean lock) {
    ReportVersion version =
        versions.findByReportIdOrderByVersionNumberDesc(report.getId()).stream()
            .findFirst()
            .orElseThrow(this::reportNotFound);
    return requireCurrentVersion(report, version.getId(), lock);
  }

  private ReportVersion requireCurrentVersion(
      InspectionReport report, UUID versionId, boolean lock) {
    ReportVersion version =
        (lock ? versions.findForUpdateById(versionId) : versions.findById(versionId))
            .filter(item -> item.getReportId().equals(report.getId()))
            .orElseThrow(this::reportNotFound);
    if (version.getVersionNumber() != report.getCurrentVersionNumber()) {
      throw reportStateConflict("Only the current report version can be changed.");
    }
    return version;
  }

  private InspectionReport requireReport(UUID reportId, boolean lock) {
    return (lock ? reports.findForUpdateById(reportId) : reports.findById(reportId))
        .orElseThrow(this::reportNotFound);
  }

  private void completeInspection(InspectionReport report) {
    Inspection inspection =
        inspections
            .findForUpdateByIdAndAuthorUserId(report.getInspectionId(), report.getAuthorUserId())
            .orElseThrow(this::inspectionNotFound);
    if (inspection.getStatus() != InspectionStatus.COMPLETED) {
      try {
        inspection.complete();
      } catch (IllegalStateException exception) {
        throw new BusinessException(
            HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", exception.getMessage());
      }
      inspections.saveAndFlush(inspection);
    }
  }

  private Inspection requireAssignedInspection(
      UserAccess.ActiveUser actor, UUID inspectionId, boolean lock) {
    Inspection inspection =
        (lock
                ? inspections.findForUpdateByIdAndAuthorUserId(inspectionId, actor.id())
                : inspections.findByIdAndAuthorUserId(inspectionId, actor.id()))
            .orElseThrow(this::inspectionScopeDenied);
    InspectionAssignment assignment =
        assignments
            .findByIdAndInspectorUserId(inspection.getAcceptedAssignmentId(), actor.id())
            .filter(value -> value.getStatus() == InspectionAssignmentStatus.ACCEPTED)
            .orElseThrow(this::inspectionScopeDenied);
    if (!assignment.getInspectorUserId().equals(actor.id())) {
      throw inspectionScopeDenied();
    }
    if (inspection.getStatus() != InspectionStatus.IN_PROGRESS
        && inspection.getStatus() != InspectionStatus.AWAITING_REPORT
        && inspection.getStatus() != InspectionStatus.COMPLETED) {
      throw reportStateConflict("The inspection is not ready for report work.");
    }
    return inspection;
  }

  private UserAccess.ActiveUser requireActiveUser(UUID userId) {
    return users.findActiveUser(userId).orElseThrow(this::reportScopeDenied);
  }

  private UserAccess.ActiveUser requireActiveRole(UUID userId, String role) {
    return users
        .findActiveUser(userId)
        .filter(user -> user.hasRole(role))
        .orElseThrow(this::reportScopeDenied);
  }

  private BusinessException inspectionScopeDenied() {
    return new BusinessException(
        HttpStatus.FORBIDDEN,
        "INSPECTION_SCOPE_DENIED",
        "The inspection is not available to this Inspector.");
  }

  private BusinessException reportScopeDenied() {
    return new BusinessException(
        HttpStatus.FORBIDDEN, "REPORT_SCOPE_DENIED", "The report is not available to this user.");
  }

  private BusinessException reportNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report was not found.");
  }

  private BusinessException inspectionNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection resource was not found.");
  }

  private BusinessException reportStateConflict(String message) {
    return new BusinessException(HttpStatus.CONFLICT, "REPORT_STATE_CONFLICT", message);
  }

  private BusinessException invalidReviewer(String message) {
    return new BusinessException(
        HttpStatus.UNPROCESSABLE_ENTITY, "REPORT_REVIEWER_INVALID", message);
  }

  private BusinessException storageUnavailable() {
    return new BusinessException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "EVIDENCE_STORAGE_UNAVAILABLE",
        "Evidence content is temporarily unavailable.");
  }

  public record EvidenceContent(
      InputStream stream, String fileName, String contentType, long sizeBytes) {}
}
