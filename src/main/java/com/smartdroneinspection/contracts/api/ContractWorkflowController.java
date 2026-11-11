package com.smartdroneinspection.contracts.api;

import com.smartdroneinspection.contracts.api.dto.request.AssignOccurrenceRequest;
import com.smartdroneinspection.contracts.api.dto.request.ConfirmOccurrencePaymentRequest;
import com.smartdroneinspection.contracts.api.dto.request.CreateServiceRequest;
import com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest;
import com.smartdroneinspection.contracts.api.dto.request.PostServiceRequestMessage;
import com.smartdroneinspection.contracts.api.dto.request.ReconcileRefundHoldRequest;
import com.smartdroneinspection.contracts.api.dto.request.RecordOccurrencePayoutRequest;
import com.smartdroneinspection.contracts.api.dto.request.RecordOccurrenceRefundRequest;
import com.smartdroneinspection.contracts.api.dto.response.OccurrenceFinanceResponse;
import com.smartdroneinspection.contracts.api.dto.response.PayOSPaymentLinkResponse;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestResponse;
import com.smartdroneinspection.contracts.api.dto.response.VnPayPaymentLinkResponse;
import com.smartdroneinspection.contracts.service.ContractWorkflowService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/contracts/requests")
public class ContractWorkflowController {

  private final ContractWorkflowService workflow;

  public ContractWorkflowController(ContractWorkflowService workflow) {
    this.workflow = workflow;
  }

  @GetMapping
  public ApiResponse<List<ServiceRequestResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(workflow.list(actorId(jwt)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ServiceRequestResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateServiceRequest request) {
    return ApiResponse.success(workflow.create(actorId(jwt), request));
  }

  @GetMapping("/{requestId}")
  public ApiResponse<ServiceRequestResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
    return ApiResponse.success(workflow.get(actorId(jwt), requestId));
  }

  @PostMapping("/{requestId}/messages")
  public ApiResponse<ServiceRequestResponse> postMessage(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @Valid @RequestBody PostServiceRequestMessage request) {
    return ApiResponse.success(workflow.postMessage(actorId(jwt), requestId, request.body()));
  }

  @PostMapping("/{requestId}/quotes")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ServiceRequestResponse> issueQuote(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @Valid @RequestBody IssueServiceQuoteRequest request) {
    return ApiResponse.success(workflow.issueQuote(actorId(jwt), requestId, request));
  }

  @PostMapping("/{requestId}/accept")
  public ApiResponse<ServiceRequestResponse> acceptQuote(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @RequestParam Integer versionNo) {
    return ApiResponse.success(workflow.acceptQuote(actorId(jwt), requestId, versionNo));
  }

  /**
   * @deprecated Legacy parent request contract upload: attaches to request and occurrence 1 for
   *     ONE_TIME. Prefer occurrence-level {@code
   *     /{requestId}/occurrences/{occurrenceId}/signed-contract}.
   */
  @Deprecated(since = "v1.1", forRemoval = false)
  @PostMapping("/{requestId}/signed-contract")
  public ApiResponse<ServiceRequestResponse> uploadSignedContract(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @RequestParam("file") MultipartFile file) {
    return ApiResponse.success(workflow.uploadSignedContract(actorId(jwt), requestId, file));
  }

  /**
   * @deprecated Legacy parent request contract download. Prefer occurrence-level {@code
   *     /{requestId}/occurrences/{occurrenceId}/contract-document}.
   */
  @Deprecated(since = "v1.1", forRemoval = false)
  @GetMapping("/{requestId}/signed-contract")
  public ResponseEntity<InputStreamResource> downloadSignedContract(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
    ContractWorkflowService.SignedContractFile file =
        workflow.openSignedContract(actorId(jwt), requestId);
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.fileName()).build().toString());
    if (file.sizeBytes() != null) {
      builder.contentLength(file.sizeBytes());
    }
    return builder.body(new InputStreamResource(file.stream()));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/signed-contract")
  public ApiResponse<ServiceRequestResponse> uploadOccurrenceSignedContract(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId,
      @RequestParam("file") MultipartFile file) {
    return ApiResponse.success(
        workflow.uploadOccurrenceSignedContract(actorId(jwt), requestId, occurrenceId, file));
  }

  @GetMapping("/{requestId}/occurrences/{occurrenceId}/contract-document")
  public ResponseEntity<InputStreamResource> downloadOccurrenceContract(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId)
      throws IOException {
    ContractWorkflowService.DownloadedContract document =
        workflow.openOccurrenceSignedContract(actorId(jwt), requestId, occurrenceId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(document.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(new InputStreamResource(document.stream()));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/assign")
  public ApiResponse<ServiceRequestResponse> assignOccurrence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId,
      @Valid @RequestBody AssignOccurrenceRequest request) {
    return ApiResponse.success(
        workflow.assignOccurrence(actorId(jwt), requestId, occurrenceId, request));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/accept-report")
  public ApiResponse<ServiceRequestResponse> customerAcceptReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId,
      @RequestParam UUID reportVersionId) {
    return ApiResponse.success(
        workflow.customerAcceptReport(actorId(jwt), requestId, occurrenceId, reportVersionId));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/confirm-payment")
  public ApiResponse<ServiceRequestResponse> confirmOccurrencePayment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId,
      @Valid @RequestBody ConfirmOccurrencePaymentRequest request) {
    return ApiResponse.success(
        workflow.confirmOccurrencePayment(actorId(jwt), requestId, occurrenceId, request));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/record-refund")
  public ApiResponse<ServiceRequestResponse> recordOccurrenceRefund(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId,
      @Valid @RequestBody RecordOccurrenceRefundRequest request) {
    return ApiResponse.success(
        workflow.recordOccurrenceRefund(actorId(jwt), requestId, occurrenceId, request));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/cancel-unpaid-order")
  public ApiResponse<ServiceRequestResponse> cancelUnpaidPaymentOrder(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId) {
    return ApiResponse.success(
        workflow.cancelUnpaidPaymentOrder(actorId(jwt), requestId, occurrenceId));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/reconcile-refund-hold")
  public ApiResponse<ServiceRequestResponse> reconcileRefundHold(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId,
      @Valid @RequestBody ReconcileRefundHoldRequest request) {
    return ApiResponse.success(
        workflow.reconcileRefundHold(actorId(jwt), requestId, occurrenceId, request));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/record-payout")
  public ApiResponse<ServiceRequestResponse> recordOccurrencePayout(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId,
      @Valid @RequestBody RecordOccurrencePayoutRequest request) {
    return ApiResponse.success(
        workflow.recordOccurrencePayout(actorId(jwt), requestId, occurrenceId, request));
  }

  @GetMapping("/{requestId}/occurrences/{occurrenceId}/finance")
  public ApiResponse<OccurrenceFinanceResponse> getOccurrenceFinance(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId) {
    return ApiResponse.success(
        workflow.getOccurrenceFinance(actorId(jwt), requestId, occurrenceId));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/payos/create-payment-link")
  public ApiResponse<PayOSPaymentLinkResponse> createPayOSPaymentLink(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId) {
    return ApiResponse.success(
        workflow.createPayOSPaymentLink(actorId(jwt), requestId, occurrenceId));
  }

  @PostMapping("/{requestId}/occurrences/{occurrenceId}/vnpay/create-payment-url")
  public ApiResponse<VnPayPaymentLinkResponse> createVnPayPaymentUrl(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID occurrenceId,
      HttpServletRequest httpRequest) {
    String clientIp = extractClientIp(httpRequest);
    return ApiResponse.success(
        workflow.createVnPayPaymentLink(actorId(jwt), requestId, occurrenceId, clientIp));
  }

  private static String extractClientIp(HttpServletRequest request) {
    if (request == null) {
      return "127.0.0.1";
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
