package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import com.smartdroneinspection.inspections.api.dto.request.ChecklistResponseRequest;
import com.smartdroneinspection.inspections.api.dto.request.CreateManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.EvidenceUploadMetadataRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingCandidateRequest;
import com.smartdroneinspection.inspections.api.dto.request.StartInspectionRequest;
import com.smartdroneinspection.inspections.api.dto.response.AiFindingCandidateResponse;
import com.smartdroneinspection.inspections.api.dto.response.ChecklistResponseResponse;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionAssignmentResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionChecklistItemResponse;
import com.smartdroneinspection.inspections.api.dto.response.StartInspectionResponse;
import com.smartdroneinspection.inspections.api.dto.response.VerifiedFindingResponse;
import com.smartdroneinspection.inspections.service.AiFindingService;
import com.smartdroneinspection.inspections.service.EvidenceService;
import com.smartdroneinspection.inspections.service.InspectionService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.exception.BusinessException;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inspections")
@PreAuthorize("hasRole('INSPECTOR')")
public class InspectionController {

  private final InspectionService inspections;
  private final EvidenceService evidence;
  private final AiFindingService findings;

  public InspectionController(
      InspectionService inspections, EvidenceService evidence, AiFindingService findings) {
    this.inspections = inspections;
    this.evidence = evidence;
    this.findings = findings;
  }

  @GetMapping("/assignments")
  public ApiResponse<List<InspectionAssignmentResponse>> assignments(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "ACCEPTED") InspectionAssignmentStatus status) {
    if (status != InspectionAssignmentStatus.ACCEPTED) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Only accepted assignments are available to the inspection client.");
    }
    return ApiResponse.success(inspections.listAcceptedAssignments(subject(jwt)));
  }

  @PostMapping("/start")
  public ApiResponse<StartInspectionResponse> start(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartInspectionRequest request) {
    return ApiResponse.success(inspections.start(subject(jwt), request.assignmentId()));
  }

  @PutMapping("/{inspectionId}/checklist-responses/{checklistItemId}")
  public ApiResponse<ChecklistResponseResponse> saveChecklistResponse(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID checklistItemId,
      @Valid @RequestBody ChecklistResponseRequest request) {
    return ApiResponse.success(
        inspections.saveChecklistResponse(subject(jwt), inspectionId, checklistItemId, request));
  }

  @GetMapping("/{inspectionId}/checklist")
  public ApiResponse<List<InspectionChecklistItemResponse>> checklist(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(inspections.listChecklist(subject(jwt), inspectionId));
  }

  @PostMapping(path = "/{inspectionId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<EvidenceResponse> uploadEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @RequestPart("file") MultipartFile file,
      @Valid @ModelAttribute EvidenceUploadMetadataRequest metadata) {
    return ApiResponse.success(evidence.upload(subject(jwt), inspectionId, file, metadata));
  }

  @GetMapping("/{inspectionId}/evidence")
  public ApiResponse<List<EvidenceResponse>> listEvidence(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(evidence.list(subject(jwt), inspectionId));
  }

  @GetMapping("/{inspectionId}/evidence/{evidenceId}/content")
  public ResponseEntity<InputStreamResource> evidenceContent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId) {
    EvidenceService.EvidenceContent content = evidence.open(subject(jwt), inspectionId, evidenceId);
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

  @GetMapping("/{inspectionId}/finding-candidates")
  public ApiResponse<List<AiFindingCandidateResponse>> findingCandidates(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(findings.listCandidates(subject(jwt), inspectionId));
  }

  @PostMapping("/{inspectionId}/evidence/{evidenceId}/analyze")
  public ApiResponse<List<AiFindingCandidateResponse>> analyzeEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId) {
    return ApiResponse.success(findings.analyze(subject(jwt), inspectionId, evidenceId));
  }

  @PostMapping("/{inspectionId}/finding-candidates/{candidateId}/review")
  public ApiResponse<AiFindingCandidateResponse> reviewCandidate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID candidateId,
      @Valid @RequestBody ReviewFindingCandidateRequest request) {
    return ApiResponse.success(
        findings.reviewCandidate(subject(jwt), inspectionId, candidateId, request));
  }

  @PostMapping("/{inspectionId}/findings")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<VerifiedFindingResponse> createManualFinding(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody CreateManualFindingRequest request) {
    return ApiResponse.success(findings.createManualFinding(subject(jwt), inspectionId, request));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
