package com.smartdroneinspection.contracts.api;

import com.smartdroneinspection.contracts.api.dto.request.AssignOccurrenceRequest;
import com.smartdroneinspection.contracts.api.dto.request.CreateServiceRequest;
import com.smartdroneinspection.contracts.api.dto.request.IssueServiceQuoteRequest;
import com.smartdroneinspection.contracts.api.dto.request.PostServiceRequestMessage;
import com.smartdroneinspection.contracts.api.dto.response.ServiceRequestResponse;
import com.smartdroneinspection.contracts.service.ContractWorkflowService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
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

  @PostMapping("/{requestId}/signed-contract")
  public ApiResponse<ServiceRequestResponse> uploadSignedContract(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @RequestParam("file") MultipartFile file) {
    return ApiResponse.success(workflow.uploadSignedContract(actorId(jwt), requestId, file));
  }

  @GetMapping("/{requestId}/signed-contract")
  public ResponseEntity<InputStreamResource> downloadSignedContract(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
    ContractWorkflowService.SignedContractFile file =
        workflow.openSignedContract(actorId(jwt), requestId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .contentLength(file.sizeBytes())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .body(new InputStreamResource(file.stream()));
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

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
