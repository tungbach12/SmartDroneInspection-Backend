package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.EvidenceQualityDecisionRequest;
import com.smartdroneinspection.inspections.api.dto.request.FindingDecisionRequest;
import com.smartdroneinspection.inspections.api.dto.request.ManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.ManualReportDraftRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewCandidateRequest;
import com.smartdroneinspection.inspections.api.dto.response.AiFindingCandidateResponse;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceQualityDecisionResponse;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportVersionResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.api.dto.response.VerifiedFindingResponse;
import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import com.smartdroneinspection.inspections.service.EvidenceQualityService;
import com.smartdroneinspection.inspections.service.EvidenceService;
import com.smartdroneinspection.inspections.service.FindingService;
import com.smartdroneinspection.inspections.service.ReportService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
  private final EvidenceQualityService qualityService;
  private final FindingService findingService;
  private final ReportService reportService;

  public InspectionController(
      EvidenceService evidenceService,
      EvidenceQualityService qualityService,
      FindingService findingService,
      ReportService reportService) {
    this.evidenceService = evidenceService;
    this.qualityService = qualityService;
    this.findingService = findingService;
    this.reportService = reportService;
  }

  @PostMapping(path = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_ADMIN')")
  public ApiResponse<EvidenceResponse> uploadEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(defaultValue = "WEB_UPLOAD") EvidenceSource source,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant captureTime,
      @RequestParam(required = false) BigDecimal latitude,
      @RequestParam(required = false) BigDecimal longitude,
      @RequestParam(required = false) String externalReference) {
    return ApiResponse.success(
        evidenceService.upload(
            actorId(jwt),
            inspectionId,
            null,
            file,
            source,
            captureTime,
            latitude,
            longitude,
            externalReference));
  }

  @GetMapping("/evidence")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_ADMIN')")
  public ApiResponse<List<EvidenceResponse>> listEvidence(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(evidenceService.list(actorId(jwt), inspectionId));
  }

  @GetMapping("/evidence/{evidenceId}/content")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_ADMIN')")
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

  @PostMapping("/evidence-quality-decisions")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceQualityDecisionResponse> decideEvidenceQuality(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody EvidenceQualityDecisionRequest request) {
    return ApiResponse.success(
        qualityService.decide(
            actorId(jwt),
            inspectionId,
            request.fieldSessionId(),
            request.decision(),
            request.shotListComparison(),
            request.limitationReason()));
  }

  @GetMapping("/evidence-quality-decisions")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_ADMIN')")
  public ApiResponse<List<EvidenceQualityDecisionResponse>> evidenceQualityHistory(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(qualityService.history(actorId(jwt), inspectionId));
  }

  @PostMapping("/evidence/{evidenceId}/analyze")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<List<AiFindingCandidateResponse>> analyzeEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId) {
    return ApiResponse.success(findingService.analyze(actorId(jwt), inspectionId, evidenceId));
  }

  @GetMapping("/finding-candidates")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_ADMIN')")
  public ApiResponse<List<AiFindingCandidateResponse>> listCandidates(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(findingService.listCandidates(actorId(jwt), inspectionId));
  }

  @PostMapping("/finding-candidates/{candidateId}/review")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ResponseEntity<ApiResponse<VerifiedFindingResponse>> reviewCandidate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID candidateId,
      @Valid @RequestBody ReviewCandidateRequest request) {
    Optional<VerifiedFindingResponse> finding =
        findingService.reviewCandidate(actorId(jwt), inspectionId, candidateId, request);
    // A rejection records a decision without creating a finding, so there is nothing to return.
    return finding
        .map(result -> ResponseEntity.ok(ApiResponse.success(result)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/findings")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<VerifiedFindingResponse> createManualFinding(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody ManualFindingRequest request) {
    return ApiResponse.success(
        findingService.createManualFinding(actorId(jwt), inspectionId, request));
  }

  @GetMapping("/findings")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_ADMIN')")
  public ApiResponse<List<VerifiedFindingResponse>> listFindings(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(findingService.listFindings(actorId(jwt), inspectionId));
  }

  @PostMapping("/findings/{findingId}/decision")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<VerifiedFindingResponse> decideFinding(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID findingId,
      @Valid @RequestBody FindingDecisionRequest request) {
    return ApiResponse.success(
        findingService.decideFinding(
            actorId(jwt), inspectionId, findingId, request.decision(), request.rationale()));
  }

  @PostMapping("/report/draft")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportVersionResponse> generateDraft(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(reportService.generateDraft(actorId(jwt), inspectionId));
  }

  /**
   * Report 3 requires a manual structured draft to remain possible when drafting is unavailable.
   */
  @PostMapping("/report/draft/manual")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportVersionResponse> authorManualDraft(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody ManualReportDraftRequest request) {
    return ApiResponse.success(
        reportService.authorManualDraft(
            actorId(jwt), inspectionId, request.narrative(), request.omissionDisclosure()));
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

  @PostMapping("/report/versions/{versionId}/review")
  @PreAuthorize("hasRole('ORG_ADMIN')")
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
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<ReportPublishedResponse> publishVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    return ApiResponse.success(reportService.publish(actorId(jwt), inspectionId, versionId));
  }

  @GetMapping("/report/versions")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_ADMIN')")
  public ApiResponse<List<InspectionReportVersionResponse>> listVersions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(reportService.listVersions(actorId(jwt), inspectionId));
  }

  @GetMapping("/report/versions/{versionId}")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'ORG_ADMIN')")
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
