package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.EvidenceMediaTypeFilter;
import com.smartdroneinspection.inspections.api.dto.EvidenceStatusFilter;
import com.smartdroneinspection.inspections.api.dto.request.BulkRetireEvidenceRequest;
import com.smartdroneinspection.inspections.api.dto.request.ConfirmFindingsRequest;
import com.smartdroneinspection.inspections.api.dto.request.FindingHandoffRequest;
import com.smartdroneinspection.inspections.api.dto.request.ManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReportSectionsRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateEvidenceLocationRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.WithdrawVersionRequest;
import com.smartdroneinspection.inspections.api.dto.response.EvidencePageResponse;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.api.dto.response.FindingResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportVersionResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionWorkflowStateResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.service.EvidenceService;
import com.smartdroneinspection.inspections.service.FindingService;
import com.smartdroneinspection.inspections.service.InspectionWorkflowService;
import com.smartdroneinspection.inspections.service.ReportService;
import com.smartdroneinspection.shared.RequestLocale;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * MF3 inspection endpoints.
 *
 * <p>The route check is navigation policy only. Organization, assignment and separation-of-duties
 * scope are enforced in the use case, so a caller with the right role but the wrong organization or
 * assignment still receives not-found rather than data.
 */
@RestController
@RequestMapping("/api/v1/inspections/{inspectionId}")
public class InspectionController {

  private final EvidenceService evidenceService;
  private final FindingService findingService;
  private final ReportService reportService;
  private final InspectionWorkflowService workflowService;

  public InspectionController(
      EvidenceService evidenceService,
      FindingService findingService,
      ReportService reportService,
      InspectionWorkflowService workflowService) {
    this.evidenceService = evidenceService;
    this.findingService = findingService;
    this.reportService = reportService;
    this.workflowService = workflowService;
  }

  @PostMapping(path = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ApiResponse<EvidenceResponse> uploadEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) UUID fieldSessionId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant captureTime,
      @RequestParam(required = false) BigDecimal latitude,
      @RequestParam(required = false) BigDecimal longitude) {
    return ApiResponse.success(
        evidenceService.upload(
            actorId(jwt), inspectionId, fieldSessionId, file, captureTime, latitude, longitude));
  }

  @GetMapping("/evidence")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ApiResponse<List<EvidenceResponse>> listEvidence(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(evidenceService.list(actorId(jwt), inspectionId));
  }

  @GetMapping("/evidence/query")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ApiResponse<EvidencePageResponse> queryEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @RequestParam(defaultValue = "ALL") EvidenceStatusFilter status,
      @RequestParam(defaultValue = "ALL") EvidenceMediaTypeFilter mediaType,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int pageSize) {
    return ApiResponse.success(
        evidenceService.query(
            actorId(jwt), inspectionId, status, mediaType, search, page, pageSize));
  }

  @PutMapping("/evidence/{evidenceId}/location")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceResponse> updateEvidenceLocation(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId,
      @Valid @RequestBody UpdateEvidenceLocationRequest request) {
    return ApiResponse.success(
        evidenceService.updateLocation(
            actorId(jwt), inspectionId, evidenceId, request.locationDescription()));
  }

  @PostMapping("/evidence/{evidenceId}/retire")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceResponse> retireEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId) {
    return ApiResponse.success(evidenceService.retire(actorId(jwt), inspectionId, evidenceId));
  }

  @PostMapping("/evidence/bulk-retire")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<List<EvidenceResponse>> bulkRetireEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @RequestBody(required = false) BulkRetireEvidenceRequest request) {
    return ApiResponse.success(
        evidenceService.bulkRetire(
            actorId(jwt), inspectionId, request == null ? null : request.evidenceIds()));
  }

  @GetMapping("/evidence/{evidenceId}/content")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ResponseEntity<InputStreamResource> evidenceContent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId) {
    EvidenceService.EvidenceContent content =
        evidenceService.open(actorId(jwt), inspectionId, evidenceId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(content.fileName()).build().toString())
        .body(new InputStreamResource(content.stream()));
  }

  @PostMapping("/evidence/{evidenceId}/analyze")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<List<FindingResponse>> analyzeEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId,
      @RequestParam(name = "replace", defaultValue = "false") boolean replace) {
    return ApiResponse.success(
        findingService.analyze(actorId(jwt), inspectionId, evidenceId, replace));
  }

  @PostMapping("/findings/{findingId}/confirmation")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FindingResponse> confirmFinding(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID findingId,
      @Valid @RequestBody ReviewFindingRequest request) {
    return ApiResponse.success(
        findingService.confirmFinding(actorId(jwt), inspectionId, findingId, request));
  }

  @PostMapping("/findings")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FindingResponse> createManualFinding(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody ManualFindingRequest request) {
    return ApiResponse.success(
        findingService.createManualFinding(actorId(jwt), inspectionId, request));
  }

  @PutMapping("/findings/{findingId}")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FindingResponse> updateFinding(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID findingId,
      @Valid @RequestBody UpdateFindingRequest request) {
    return ApiResponse.success(
        findingService.updateFinding(actorId(jwt), inspectionId, findingId, request));
  }

  @DeleteMapping("/findings/{findingId}")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<Void> deleteFinding(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID findingId) {
    findingService.deleteFinding(actorId(jwt), inspectionId, findingId);
    return ApiResponse.success(null);
  }

  /** Explicitly deleted Inspector findings remain available with deletion attribution. */
  @GetMapping("/findings/removed")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ApiResponse<List<FindingResponse>> listRemovedFindings(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(findingService.listRemovedFindings(actorId(jwt), inspectionId));
  }

  @PostMapping("/findings/{findingId}/restore")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<FindingResponse> restoreFinding(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID findingId) {
    return ApiResponse.success(
        findingService.restoreFinding(actorId(jwt), inspectionId, findingId));
  }

  @GetMapping("/workflow-state")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ApiResponse<InspectionWorkflowStateResponse> workflowState(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(workflowService.state(actorId(jwt), inspectionId));
  }

  @PostMapping("/findings/reopen")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<Void> reopenFindings(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    findingService.reopenFindings(actorId(jwt), inspectionId);
    return ApiResponse.success(null);
  }

  @PostMapping("/findings/confirm")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<Void> confirmFindings(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody ConfirmFindingsRequest request) {
    findingService.confirmFindings(actorId(jwt), inspectionId, request.noFindingsAcknowledged());
    return ApiResponse.success(null);
  }

  @GetMapping("/findings")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ApiResponse<List<FindingResponse>> listFindings(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(findingService.listFindings(actorId(jwt), inspectionId));
  }

  @PostMapping("/report/draft")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportVersionResponse> generateDraft(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      HttpServletRequest request) {
    return ApiResponse.success(
        reportService.generateDraft(actorId(jwt), inspectionId, RequestLocale.from(request)));
  }

  /**
   * Report 3 requires a manual structured draft to remain possible when drafting is unavailable.
   */
  @PostMapping("/report/draft/manual")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportVersionResponse> authorManualDraft(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(reportService.authorManualDraft(actorId(jwt), inspectionId));
  }

  @PostMapping("/report/versions/{versionId}/verify")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportVersionResponse> verifyVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    return ApiResponse.success(reportService.verifyVersion(actorId(jwt), inspectionId, versionId));
  }

  @PostMapping("/report/versions/{versionId}/submit")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportVersionResponse> submitVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    return ApiResponse.success(reportService.submitVersion(actorId(jwt), inspectionId, versionId));
  }

  @PostMapping("/report/versions/{versionId}/withdraw")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportVersionResponse> withdrawVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      @Valid @RequestBody(required = false) WithdrawVersionRequest request) {
    return ApiResponse.success(
        reportService.withdrawVersion(
            actorId(jwt), inspectionId, versionId, request == null ? null : request.reason()));
  }

  @PostMapping("/report/versions/{versionId}/review")
  @PreAuthorize("hasRole('ORG_MANAGER')")
  public ApiResponse<InspectionReportVersionResponse> reviewVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      @RequestParam boolean approve,
      @RequestParam(required = false) String reason) {
    return ApiResponse.success(
        reportService.reviewVersion(actorId(jwt), inspectionId, versionId, approve, reason));
  }

  @PostMapping("/report/versions/{versionId}/publish")
  @PreAuthorize("hasRole('ORG_MANAGER')")
  public ApiResponse<ReportPublishedResponse> publishVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      HttpServletRequest request) {
    return ApiResponse.success(
        reportService.publish(actorId(jwt), inspectionId, versionId, RequestLocale.from(request)));
  }

  @PostMapping("/report/corrective-handoff")
  @PreAuthorize("hasRole('ORG_MANAGER')")
  public ApiResponse<ReportPublishedResponse> completeCorrectiveHandoff(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody FindingHandoffRequest request) {
    return ApiResponse.success(
        reportService.completeCorrectiveActionHandoff(
            actorId(jwt), inspectionId, request.findingIds()));
  }

  @PutMapping("/report/versions/{versionId}/sections")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportVersionResponse> saveSections(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      @Valid @RequestBody ReportSectionsRequest request) {
    return ApiResponse.success(
        reportService.saveSections(actorId(jwt), inspectionId, versionId, request));
  }

  @GetMapping("/report/versions/{versionId}/preview")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ResponseEntity<byte[]> previewReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      HttpServletRequest request) {
    return reportService.preview(
        actorId(jwt), inspectionId, versionId, RequestLocale.from(request));
  }

  @GetMapping("/report/versions/{versionId}/document")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ResponseEntity<byte[]> downloadReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    return reportService.downloadArtefact(actorId(jwt), inspectionId, versionId);
  }

  @GetMapping("/report/versions")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ApiResponse<List<InspectionReportVersionResponse>> listVersions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(reportService.listVersions(actorId(jwt), inspectionId));
  }

  @GetMapping("/report/versions/{versionId}")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_MANAGER')")
  public ApiResponse<InspectionReportVersionResponse> getVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    return ApiResponse.success(reportService.getVersion(actorId(jwt), inspectionId, versionId));
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
