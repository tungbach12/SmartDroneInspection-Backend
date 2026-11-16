package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.EvidenceMediaTypeFilter;
import com.smartdroneinspection.inspections.api.dto.EvidenceStatusFilter;
import com.smartdroneinspection.inspections.api.dto.request.AnalyzeVideoRequest;
import com.smartdroneinspection.inspections.api.dto.request.BeginEvidenceUploadRequest;
import com.smartdroneinspection.inspections.api.dto.request.BulkRetireEvidenceRequest;
import com.smartdroneinspection.inspections.api.dto.request.ConfirmFindingsRequest;
import com.smartdroneinspection.inspections.api.dto.request.DeclineInspectionRequest;
import com.smartdroneinspection.inspections.api.dto.request.FindingSourceFilter;
import com.smartdroneinspection.inspections.api.dto.request.FindingStatusFilter;
import com.smartdroneinspection.inspections.api.dto.request.ManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.PromoteVideoKeyframesRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateEvidenceLocationRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.WithdrawVersionRequest;
import com.smartdroneinspection.inspections.api.dto.response.EvidencePageResponse;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceUploadSessionResponse;
import com.smartdroneinspection.inspections.api.dto.response.FindingPageResponse;
import com.smartdroneinspection.inspections.api.dto.response.FindingResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionReportResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionWorkflowStateResponse;
import com.smartdroneinspection.inspections.api.dto.response.MediaTicketResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportAiSummaryResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReportPublishedResponse;
import com.smartdroneinspection.inspections.api.dto.response.VideoAutoDetectResponse;
import com.smartdroneinspection.inspections.api.dto.response.VideoScanResponse;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.service.EvidenceService;
import com.smartdroneinspection.inspections.service.EvidenceUploadSessionService;
import com.smartdroneinspection.inspections.service.FindingService;
import com.smartdroneinspection.inspections.service.InspectionCreationService;
import com.smartdroneinspection.inspections.service.InspectionWorkflowService;
import com.smartdroneinspection.inspections.service.MediaTicketService;
import com.smartdroneinspection.inspections.service.ReportService;
import com.smartdroneinspection.shared.RequestLocale;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.api.InspectionReportAcceptancePort;
import com.smartdroneinspection.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
  private final InspectionCreationService creationService;
  private final java.util.Optional<
          com.smartdroneinspection.shared.api.InspectionReportAcceptancePort>
      reportAcceptancePort;
  private final Optional<EvidenceUploadSessionService> uploadSessionService;
  private final Optional<MediaTicketService> mediaTicketService;

  public InspectionController(
      EvidenceService evidenceService,
      FindingService findingService,
      ReportService reportService,
      InspectionWorkflowService workflowService,
      InspectionCreationService creationService) {
    this(
        evidenceService,
        findingService,
        reportService,
        workflowService,
        creationService,
        java.util.Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  public InspectionController(
      EvidenceService evidenceService,
      FindingService findingService,
      ReportService reportService,
      InspectionWorkflowService workflowService,
      InspectionCreationService creationService,
      java.util.Optional<com.smartdroneinspection.shared.api.InspectionReportAcceptancePort>
          reportAcceptancePort) {
    this(
        evidenceService,
        findingService,
        reportService,
        workflowService,
        creationService,
        reportAcceptancePort,
        Optional.empty(),
        Optional.empty());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public InspectionController(
      EvidenceService evidenceService,
      FindingService findingService,
      ReportService reportService,
      InspectionWorkflowService workflowService,
      InspectionCreationService creationService,
      java.util.Optional<com.smartdroneinspection.shared.api.InspectionReportAcceptancePort>
          reportAcceptancePort,
      Optional<EvidenceUploadSessionService> uploadSessionService,
      Optional<MediaTicketService> mediaTicketService) {
    this.evidenceService = evidenceService;
    this.findingService = findingService;
    this.reportService = reportService;
    this.workflowService = workflowService;
    this.creationService = creationService;
    this.reportAcceptancePort = reportAcceptancePort;
    this.uploadSessionService = uploadSessionService;
    this.mediaTicketService = mediaTicketService;
  }

  @PostMapping(path = "/flight-permit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<com.smartdroneinspection.inspections.api.dto.response.InspectionResponse>
      uploadFlightPermit(
          @AuthenticationPrincipal Jwt jwt,
          @PathVariable UUID inspectionId,
          @RequestParam("file") MultipartFile file,
          @RequestParam("permitNumber") String permitNumber,
          @RequestParam("issuingAuthority") String issuingAuthority,
          @RequestParam("validFrom") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
              Instant validFrom,
          @RequestParam("validTo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
              Instant validTo,
          @RequestParam(value = "droneSerial", required = false) String droneSerial,
          @RequestParam(value = "maxAltitudeM", required = false) Integer maxAltitudeM)
          throws java.io.IOException {
    return ApiResponse.success(
        creationService.uploadFlightPermit(
            actorId(jwt),
            inspectionId,
            null,
            file.getOriginalFilename(),
            file.getBytes(),
            file.getContentType(),
            permitNumber,
            issuingAuthority,
            validFrom,
            validTo,
            droneSerial,
            maxAltitudeM));
  }

  @PostMapping(path = "/flight-permit", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<com.smartdroneinspection.inspections.api.dto.response.InspectionResponse>
      uploadFlightPermitJson(
          @AuthenticationPrincipal Jwt jwt,
          @PathVariable UUID inspectionId,
          @Valid @RequestBody
              com.smartdroneinspection.inspections.api.dto.request.UploadFlightPermitRequest
                  request) {
    return ApiResponse.success(
        creationService.uploadFlightPermit(
            actorId(jwt),
            inspectionId,
            request.fileKey(),
            request.fileName(),
            null,
            null,
            request.permitNumber(),
            request.issuingAuthority(),
            request.validFrom(),
            request.validTo(),
            request.droneSerial(),
            request.maxAltitudeM()));
  }

  @PostMapping("/flight-permit/review")
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public ApiResponse<com.smartdroneinspection.inspections.api.dto.response.InspectionResponse>
      reviewFlightPermit(
          @AuthenticationPrincipal Jwt jwt,
          @PathVariable UUID inspectionId,
          @Valid @RequestBody
              com.smartdroneinspection.inspections.api.dto.request.ReviewFlightPermitRequest
                  request) {
    return ApiResponse.success(
        creationService.reviewFlightPermit(
            actorId(jwt), inspectionId, request.decision(), request.reason(), request.notes()));
  }

  @GetMapping("/flight-permit/content")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ResponseEntity<byte[]> flightPermitContent(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    InspectionCreationService.FlightPermitContent content =
        creationService.openFlightPermit(actorId(jwt), inspectionId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .contentLength(content.bytes().length)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(content.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(content.bytes());
  }

  @PutMapping("/schedule")
  @PreAuthorize("hasRole('MANAGER')")
  public ApiResponse<com.smartdroneinspection.inspections.api.dto.response.InspectionResponse>
      reschedule(
          @AuthenticationPrincipal Jwt jwt,
          @PathVariable UUID inspectionId,
          @Valid @RequestBody
              com.smartdroneinspection.inspections.api.dto.request.RescheduleInspectionRequest
                  request) {
    return ApiResponse.success(
        creationService.reschedule(
            actorId(jwt), inspectionId, request.plannedStartAt(), request.plannedEndAt()));
  }

  @PutMapping("/assignment")
  @PreAuthorize("hasRole('MANAGER')")
  public ApiResponse<com.smartdroneinspection.inspections.api.dto.response.InspectionResponse>
      reassign(
          @AuthenticationPrincipal Jwt jwt,
          @PathVariable UUID inspectionId,
          @Valid @RequestBody
              com.smartdroneinspection.inspections.api.dto.request.ReassignInspectionRequest
                  request) {
    return ApiResponse.success(
        creationService.reassign(
            actorId(jwt), inspectionId, request.inspectorId(), request.droneId()));
  }

  @PostMapping("/cancellation")
  @PreAuthorize("hasRole('MANAGER')")
  public ApiResponse<com.smartdroneinspection.inspections.api.dto.response.InspectionResponse>
      cancel(
          @AuthenticationPrincipal Jwt jwt,
          @PathVariable UUID inspectionId,
          @RequestBody(required = false)
              com.smartdroneinspection.inspections.api.dto.request.CancelInspectionRequest
                  request) {
    return ApiResponse.success(
        creationService.cancel(
            actorId(jwt), inspectionId, request != null ? request.reason() : null));
  }

  @PostMapping("/accept")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<com.smartdroneinspection.inspections.api.dto.response.InspectionResponse>
      accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(creationService.acceptAssignment(actorId(jwt), inspectionId));
  }

  @PostMapping("/decline")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<com.smartdroneinspection.inspections.api.dto.response.InspectionResponse>
      decline(
          @AuthenticationPrincipal Jwt jwt,
          @PathVariable UUID inspectionId,
          @Valid @RequestBody DeclineInspectionRequest request) {
    return ApiResponse.success(
        creationService.decline(actorId(jwt), inspectionId, request.reason()));
  }

  @PostMapping("/reassign-candidates")
  @PreAuthorize("hasRole('MANAGER')")
  public ApiResponse<
          List<
              com.smartdroneinspection.shared.api.InspectionReassignCandidatePort
                  .ReassignCandidate>>
      reassignCandidates(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(
        creationService.evaluateReassignCandidates(actorId(jwt), inspectionId));
  }

  @PostMapping(path = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<EvidenceResponse> uploadEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) UUID fieldSessionId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant captureTime,
      @RequestParam(required = false) BigDecimal latitude,
      @RequestParam(required = false) BigDecimal longitude,
      @RequestParam(required = false) UUID sourceVideoEvidenceId,
      @RequestParam(required = false) Double sourceTimestampSeconds) {
    return ApiResponse.success(
        evidenceService.upload(
            actorId(jwt),
            inspectionId,
            fieldSessionId,
            file,
            captureTime,
            latitude,
            longitude,
            sourceVideoEvidenceId,
            sourceTimestampSeconds));
  }

  @GetMapping("/evidence")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<List<EvidenceResponse>> listEvidence(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(evidenceService.list(actorId(jwt), inspectionId));
  }

  @GetMapping("/evidence/query")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
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

  @PostMapping("/evidence/uploads")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceUploadSessionResponse> beginEvidenceUpload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody BeginEvidenceUploadRequest request) {
    return ApiResponse.success(
        requireUploadSessionService().beginUpload(actorId(jwt), inspectionId, request));
  }

  @GetMapping("/evidence/uploads/{sessionId}")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceUploadSessionResponse> getEvidenceUploadStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId) {
    return ApiResponse.success(
        requireUploadSessionService().getStatus(actorId(jwt), inspectionId, sessionId));
  }

  @PutMapping(
      path = "/evidence/uploads/{sessionId}/parts/{partNumber}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceUploadSessionResponse> uploadEvidencePart(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId,
      @PathVariable int partNumber,
      @RequestParam("file") MultipartFile file) {
    return ApiResponse.success(
        requireUploadSessionService()
            .uploadPart(actorId(jwt), inspectionId, sessionId, partNumber, file));
  }

  @PostMapping("/evidence/uploads/{sessionId}/complete")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceUploadSessionResponse> completeEvidenceUpload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId) {
    return ApiResponse.success(
        requireUploadSessionService().completeUpload(actorId(jwt), inspectionId, sessionId));
  }

  @DeleteMapping("/evidence/uploads/{sessionId}")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceUploadSessionResponse> cancelEvidenceUpload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID sessionId) {
    return ApiResponse.success(
        requireUploadSessionService().cancelUpload(actorId(jwt), inspectionId, sessionId));
  }

  @PostMapping("/evidence/{evidenceId}/retry-processing")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<EvidenceResponse> retryEvidenceProcessing(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId) {
    return ApiResponse.success(
        evidenceService.retryProcessing(actorId(jwt), inspectionId, evidenceId));
  }

  @PostMapping("/evidence/{evidenceId}/media-ticket")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<MediaTicketResponse> createMediaTicket(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId,
      @RequestParam(name = "variant", defaultValue = "primary") String variant) {
    UUID actor = actorId(jwt);
    evidenceService.resolveMediaDescriptor(actor, inspectionId, evidenceId, variant, true);
    return ApiResponse.success(
        requireMediaTicketService().issueTicket(actor, inspectionId, evidenceId, variant));
  }

  @GetMapping("/evidence/{evidenceId}/content")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ResponseEntity<InputStreamResource> evidenceContent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId,
      @RequestParam(name = "variant", defaultValue = "primary") String variant,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
      @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRangeHeader,
      HttpServletRequest request) {
    EvidenceService.MediaDescriptor descriptor =
        evidenceService.resolveMediaDescriptor(
            actorId(jwt), inspectionId, evidenceId, variant, false);
    boolean inline =
        "thumb".equals(MediaTicketService.normalizeVariant(variant)) || rangeHeader != null;
    return serveMedia(descriptor, rangeHeader, ifRangeHeader, request, inline);
  }

  @GetMapping("/evidence/{evidenceId}/stream")
  public ResponseEntity<InputStreamResource> streamEvidenceMedia(
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId,
      @RequestParam("ticket") String ticket,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
      @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRangeHeader,
      HttpServletRequest request) {
    MediaTicketService.VerifiedTicket verified =
        requireMediaTicketService().verifyTicket(ticket, inspectionId, evidenceId);
    EvidenceService.MediaDescriptor descriptor =
        evidenceService.resolveMediaDescriptor(
            verified.actorId(), inspectionId, evidenceId, verified.variant(), true);
    return serveMedia(descriptor, rangeHeader, ifRangeHeader, request, true);
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

  @PostMapping("/evidence/{evidenceId}/analyze-video")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<VideoAutoDetectResponse> analyzeVideoEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId,
      @RequestBody AnalyzeVideoRequest request) {
    return ApiResponse.success(
        findingService.analyzeVideo(actorId(jwt), inspectionId, evidenceId, request));
  }

  @PostMapping("/evidence/{evidenceId}/video-scan")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<VideoScanResponse> scanVideoEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId,
      @RequestParam(name = "intervalSeconds", defaultValue = "1.0") double intervalSeconds,
      @RequestParam(name = "maxFrames", defaultValue = "30") int maxFrames) {
    return ApiResponse.success(
        findingService.scanVideo(
            actorId(jwt), inspectionId, evidenceId, intervalSeconds, maxFrames));
  }

  @PostMapping("/evidence/{evidenceId}/video-keyframes")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<VideoAutoDetectResponse> promoteVideoKeyframes(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID evidenceId,
      @RequestBody PromoteVideoKeyframesRequest request) {
    return ApiResponse.success(
        findingService.promoteVideoKeyframes(actorId(jwt), inspectionId, evidenceId, request));
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
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
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
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
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
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<List<FindingResponse>> listFindings(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(findingService.listFindings(actorId(jwt), inspectionId));
  }

  @GetMapping("/findings/query")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<FindingPageResponse> queryFindings(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @RequestParam(defaultValue = "ACTIVE") FindingStatusFilter status,
      @RequestParam(required = false) FindingSeverity severity,
      @RequestParam(defaultValue = "ALL") FindingSourceFilter source,
      @RequestParam(required = false) String defectQuery,
      @RequestParam(required = false) String evidenceQuery,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int pageSize) {
    return ApiResponse.success(
        findingService.query(
            actorId(jwt),
            inspectionId,
            status,
            severity,
            source,
            defectQuery,
            evidenceQuery,
            page,
            pageSize));
  }

  @PostMapping("/report/draft")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportResponse> generateDraft(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @RequestBody(required = false) java.util.Map<String, String> body,
      HttpServletRequest request) {
    String providedSummary = body == null ? null : body.get("summary");
    return ApiResponse.success(
        reportService.generateDraft(
            actorId(jwt), inspectionId, RequestLocale.from(request), providedSummary));
  }

  /**
   * Report 3 requires a manual structured draft to remain possible when drafting is unavailable.
   */
  @PostMapping("/report/draft/manual")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportResponse> authorManualDraft(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(reportService.authorManualDraft(actorId(jwt), inspectionId));
  }

  @PostMapping("/report/versions/{versionId}/verify")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportResponse> verifyVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    return ApiResponse.success(reportService.verifyVersion(actorId(jwt), inspectionId, versionId));
  }

  @PostMapping("/report/versions/{versionId}/submit")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportResponse> submitVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    return ApiResponse.success(reportService.submitVersion(actorId(jwt), inspectionId, versionId));
  }

  @PostMapping("/report/versions/{versionId}/withdraw")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<InspectionReportResponse> withdrawVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      @Valid @RequestBody(required = false) WithdrawVersionRequest request) {
    return ApiResponse.success(
        reportService.withdrawVersion(
            actorId(jwt), inspectionId, versionId, request == null ? null : request.reason()));
  }

  @PostMapping("/report/versions/{versionId}/review")
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public ApiResponse<ReportPublishedResponse> reviewVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      @RequestParam(required = false) Boolean approve,
      @RequestParam(required = false) String reason,
      HttpServletRequest request) {
    return ApiResponse.success(
        reportService.reviewVersion(
            actorId(jwt), inspectionId, versionId, approve, reason, RequestLocale.from(request)));
  }

  @PostMapping("/report/versions/{versionId}/accept")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<Void> acceptReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    if (reportAcceptancePort.isPresent()) {
      reportAcceptancePort.get().acceptReport(actorId(jwt), inspectionId, versionId);
    }
    return ApiResponse.success(null);
  }

  @PostMapping("/report/versions/{versionId}/clarification")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<ReportPublishedResponse> requestReportClarification(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      @RequestParam String reason) {
    return ApiResponse.success(
        reportService.requestCustomerClarification(actorId(jwt), inspectionId, versionId, reason));
  }

  @PostMapping("/payos/create-payment-link")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<InspectionReportAcceptancePort.PayOSCheckoutDetails> createPayOSPaymentLink(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    if (reportAcceptancePort.isEmpty()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PAYMENT_GATEWAY_UNAVAILABLE",
          "Inspection payment service is not configured");
    }
    return ApiResponse.success(
        reportAcceptancePort.get().createPayOSPaymentLink(actorId(jwt), inspectionId));
  }

  @PostMapping("/vnpay/create-payment-url")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<InspectionReportAcceptancePort.VnPayCheckoutDetails> createVnPayPaymentUrl(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      HttpServletRequest httpRequest) {
    if (reportAcceptancePort.isEmpty()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PAYMENT_GATEWAY_UNAVAILABLE",
          "Inspection payment service is not configured");
    }
    String forwarded = httpRequest.getHeader("X-Forwarded-For");
    String clientIp =
        (forwarded != null && !forwarded.isBlank())
            ? forwarded.split(",")[0].trim()
            : (httpRequest.getRemoteAddr() != null ? httpRequest.getRemoteAddr() : "127.0.0.1");
    return ApiResponse.success(
        reportAcceptancePort.get().createVnPayPaymentLink(actorId(jwt), inspectionId, clientIp));
  }

  @GetMapping("/report/payment-status")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER', 'ADMIN', 'INSPECTOR')")
  public ApiResponse<InspectionReportAcceptancePort.PaymentStatusDetails> getReportPaymentStatus(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    if (reportAcceptancePort.isEmpty()) {
      return ApiResponse.success(
          new InspectionReportAcceptancePort.PaymentStatusDetails(
              false, false, "NOT_APPLICABLE", null, null, null));
    }
    return ApiResponse.success(
        reportAcceptancePort.get().getPaymentStatus(actorId(jwt), inspectionId));
  }

  @GetMapping("/report/versions/{versionId}/ai-summary")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'MANAGER', 'ADMIN', 'CUSTOMER')")
  public ApiResponse<ReportAiSummaryResponse> getAiSummary(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      HttpServletRequest request) {
    return ApiResponse.success(
        reportService.getOrGenerateAiSummary(
            actorId(jwt), inspectionId, versionId, RequestLocale.from(request)));
  }

  @GetMapping("/report/versions/{versionId}/preview")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'MANAGER', 'ADMIN')")
  public ResponseEntity<byte[]> previewReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId,
      HttpServletRequest request) {
    return reportService.preview(
        actorId(jwt), inspectionId, versionId, RequestLocale.from(request));
  }

  @GetMapping("/report/versions/{versionId}/document")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ResponseEntity<byte[]> downloadReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    return reportService.downloadArtefact(actorId(jwt), inspectionId, versionId);
  }

  @GetMapping("/report/versions")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<List<InspectionReportResponse>> listVersions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(reportService.listVersions(actorId(jwt), inspectionId));
  }

  @GetMapping("/report/versions/{versionId}")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER', 'MANAGER', 'ADMIN')")
  public ApiResponse<InspectionReportResponse> getVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID versionId) {
    return ApiResponse.success(reportService.getVersion(actorId(jwt), inspectionId, versionId));
  }

  private EvidenceUploadSessionService requireUploadSessionService() {
    return uploadSessionService.orElseThrow(
        () ->
            new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "UPLOAD_SESSION_UNAVAILABLE",
                "Resumable upload service is not configured"));
  }

  private MediaTicketService requireMediaTicketService() {
    return mediaTicketService.orElseThrow(
        () ->
            new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "MEDIA_TICKET_UNAVAILABLE",
                "Media ticket service is not configured"));
  }

  private ResponseEntity<InputStreamResource> serveMedia(
      EvidenceService.MediaDescriptor descriptor,
      String rangeHeader,
      String ifRangeHeader,
      HttpServletRequest request,
      boolean inlineDisposition) {
    long totalBytes = descriptor.totalBytes();
    String etag =
        "\""
            + Integer.toHexString(descriptor.objectKey().hashCode())
            + "-"
            + Long.toHexString(totalBytes)
            + "\"";
    ContentDisposition disposition =
        (inlineDisposition ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(descriptor.fileName())
            .build();
    MediaType mediaType = MediaType.parseMediaType(descriptor.contentType());
    boolean isHead = HttpMethod.HEAD.matches(request.getMethod());

    boolean applyRange =
        rangeHeader != null
            && !rangeHeader.isBlank()
            && (ifRangeHeader == null
                || ifRangeHeader.isBlank()
                || ifRangeHeader.trim().equals(etag));

    if (!applyRange) {
      ResponseEntity.BodyBuilder builder =
          ResponseEntity.ok()
              .contentType(mediaType)
              .contentLength(totalBytes)
              .eTag(etag)
              .header(HttpHeaders.ACCEPT_RANGES, "bytes")
              .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
              .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
      if (isHead) {
        return builder.build();
      }
      InputStream stream = evidenceService.openStream(descriptor.objectKey());
      return builder.body(new SizedInputStreamResource(stream, totalBytes));
    }

    ByteRange parsedRange = parseSingleByteRange(rangeHeader, totalBytes);
    if (parsedRange == null) {
      return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .header(HttpHeaders.CONTENT_RANGE, "bytes */" + totalBytes)
          .build();
    }

    long rangeLength = parsedRange.end() - parsedRange.start() + 1;
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .contentType(mediaType)
            .contentLength(rangeLength)
            .eTag(etag)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(
                HttpHeaders.CONTENT_RANGE,
                "bytes " + parsedRange.start() + "-" + parsedRange.end() + "/" + totalBytes)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
    if (isHead) {
      return builder.build();
    }
    InputStream stream =
        evidenceService.openRangeStream(descriptor.objectKey(), parsedRange.start(), rangeLength);
    return builder.body(new SizedInputStreamResource(stream, rangeLength));
  }

  private static final class SizedInputStreamResource extends InputStreamResource {
    private final long sizeBytes;

    private SizedInputStreamResource(InputStream inputStream, long sizeBytes) {
      super(inputStream);
      this.sizeBytes = sizeBytes;
    }

    @Override
    public long contentLength() {
      return sizeBytes;
    }
  }

  private static ByteRange parseSingleByteRange(String header, long totalBytes) {
    if (totalBytes <= 0 || header == null) {
      return null;
    }
    String trimmed = header.trim();
    if (!trimmed.regionMatches(true, 0, "bytes=", 0, 6)) {
      return null;
    }
    String spec = trimmed.substring(6).trim();
    if (spec.isEmpty() || spec.contains(",")) {
      return null;
    }
    int dash = spec.indexOf('-');
    if (dash < 0) {
      return null;
    }
    String startPart = spec.substring(0, dash).trim();
    String endPart = spec.substring(dash + 1).trim();
    try {
      if (startPart.isEmpty()) {
        long suffixLength = Long.parseLong(endPart);
        if (suffixLength <= 0) {
          return null;
        }
        long start = Math.max(0L, totalBytes - suffixLength);
        return new ByteRange(start, totalBytes - 1);
      }
      long start = Long.parseLong(startPart);
      if (start < 0 || start >= totalBytes) {
        return null;
      }
      long end =
          endPart.isEmpty() ? totalBytes - 1 : Math.min(Long.parseLong(endPart), totalBytes - 1);
      if (end < start) {
        return null;
      }
      return new ByteRange(start, end);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private record ByteRange(long start, long end) {}

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
