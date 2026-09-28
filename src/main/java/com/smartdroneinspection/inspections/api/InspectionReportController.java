package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.request.AssignPeerReviewerRequest;
import com.smartdroneinspection.inspections.api.dto.request.ClientReportDecisionRequest;
import com.smartdroneinspection.inspections.api.dto.request.PeerReviewDecisionRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateReportNarrativeRequest;
import com.smartdroneinspection.inspections.api.dto.response.ReportVersionResponse;
import com.smartdroneinspection.inspections.service.InspectionReportService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InspectionReportController {

  private final InspectionReportService reports;

  public InspectionReportController(InspectionReportService reports) {
    this.reports = reports;
  }

  @GetMapping("/reports")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'SERVICE_MANAGER', 'CLIENT')")
  public ApiResponse<List<ReportVersionResponse>> listReports(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(reports.listReports(subject(jwt)));
  }

  @GetMapping("/reports/{reportId}")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'SERVICE_MANAGER', 'CLIENT')")
  public ApiResponse<ReportVersionResponse> getReport(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId) {
    return ApiResponse.success(reports.getReport(subject(jwt), reportId));
  }

  @PostMapping("/inspections/{inspectionId}/report")
  @PreAuthorize("hasRole('INSPECTOR')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ReportVersionResponse> createDraft(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(reports.createDraft(subject(jwt), inspectionId));
  }

  @GetMapping("/inspections/{inspectionId}/report")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<ReportVersionResponse> getInspectionReport(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(reports.getInspectionReport(subject(jwt), inspectionId));
  }

  @PostMapping("/reports/{reportId}/versions")
  @PreAuthorize("hasRole('INSPECTOR')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ReportVersionResponse> createRevision(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId) {
    return ApiResponse.success(reports.createRevision(subject(jwt), reportId));
  }

  @PostMapping("/reports/{reportId}/versions/{versionId}/ai-draft")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<ReportVersionResponse> generateAiDraft(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId, @PathVariable UUID versionId) {
    return ApiResponse.success(reports.generateAiDraft(subject(jwt), reportId, versionId));
  }

  @PutMapping("/reports/{reportId}/versions/{versionId}/narrative")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<ReportVersionResponse> updateNarrative(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID reportId,
      @PathVariable UUID versionId,
      @RequestBody UpdateReportNarrativeRequest request) {
    if (request.text() == null || request.text().isBlank()) {
      throw new IllegalArgumentException("Narrative text must not be blank.");
    }
    return ApiResponse.success(
        reports.updateNarrative(subject(jwt), reportId, versionId, request.text()));
  }

  @PutMapping("/reports/{reportId}/versions/{versionId}/reviewer")
  @PreAuthorize("hasRole('SERVICE_MANAGER')")
  public ApiResponse<ReportVersionResponse> assignReviewer(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID reportId,
      @PathVariable UUID versionId,
      @Valid @RequestBody AssignPeerReviewerRequest request) {
    return ApiResponse.success(
        reports.assignReviewer(subject(jwt), reportId, versionId, request.reviewerId()));
  }

  @PostMapping("/reports/{reportId}/versions/{versionId}/submit-review")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<ReportVersionResponse> submitForReview(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId, @PathVariable UUID versionId) {
    return ApiResponse.success(reports.submitForReview(subject(jwt), reportId, versionId));
  }

  @PostMapping("/reports/{reportId}/versions/{versionId}/review")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<ReportVersionResponse> review(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID reportId,
      @PathVariable UUID versionId,
      @Valid @RequestBody PeerReviewDecisionRequest request) {
    return ApiResponse.success(reports.review(subject(jwt), reportId, versionId, request));
  }

  @PostMapping("/reports/{reportId}/versions/{versionId}/release")
  @PreAuthorize("hasRole('SERVICE_MANAGER')")
  public ApiResponse<ReportVersionResponse> release(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId, @PathVariable UUID versionId) {
    return ApiResponse.success(reports.release(subject(jwt), reportId, versionId));
  }

  @PostMapping("/reports/{reportId}/versions/{versionId}/client-decision")
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<ReportVersionResponse> clientDecision(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID reportId,
      @PathVariable UUID versionId,
      @Valid @RequestBody ClientReportDecisionRequest request) {
    return ApiResponse.success(reports.clientDecision(subject(jwt), reportId, versionId, request));
  }

  @GetMapping("/reports/{reportId}/versions/{versionId}/evidence/{evidenceId}/content")
  @PreAuthorize("hasRole('CLIENT')")
  public ResponseEntity<InputStreamResource> releasedEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID reportId,
      @PathVariable UUID versionId,
      @PathVariable UUID evidenceId) {
    InspectionReportService.EvidenceContent content =
        reports.openReleasedEvidence(subject(jwt), reportId, versionId, evidenceId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .contentLength(content.sizeBytes())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(content.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body(new InputStreamResource(content.stream()));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
