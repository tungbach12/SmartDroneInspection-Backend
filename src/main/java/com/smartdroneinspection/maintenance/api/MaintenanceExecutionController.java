package com.smartdroneinspection.maintenance.api;

import com.smartdroneinspection.maintenance.api.dto.request.ChangeDecisionRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateChangeOrderRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateReportVersionRequest;
import com.smartdroneinspection.maintenance.api.dto.request.ReconcileCostsRequest;
import com.smartdroneinspection.maintenance.api.dto.request.RecordAcceptanceRequest;
import com.smartdroneinspection.maintenance.api.dto.request.RecordWorkLogRequest;
import com.smartdroneinspection.maintenance.api.dto.response.AcceptanceDecisionResponse;
import com.smartdroneinspection.maintenance.api.dto.response.ChangeOrderResponse;
import com.smartdroneinspection.maintenance.api.dto.response.CostReconciliationResponse;
import com.smartdroneinspection.maintenance.api.dto.response.ReportVersionResponse;
import com.smartdroneinspection.maintenance.api.dto.response.WorkLogResponse;
import com.smartdroneinspection.maintenance.api.dto.response.WorkOrderResponse;
import com.smartdroneinspection.maintenance.service.MaintenanceExecutionService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MF4 execution, change control, completion reporting, acceptance and closure endpoints.
 *
 * <p>Each transition is a named action. Route checks express navigation policy only; the service
 * enforces organization, team membership and reviewer independence, so knowing an identifier is
 * never enough to act on another team's work.
 */
@RestController
@RequestMapping("/api/v1/maintenance/work-orders/{workOrderId}")
public class MaintenanceExecutionController {

  private final MaintenanceExecutionService executionService;

  public MaintenanceExecutionController(MaintenanceExecutionService executionService) {
    this.executionService = executionService;
  }

  @PostMapping("/resume-after-rework")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<WorkOrderResponse> resumeAfterRework(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.resumeAfterRework(actorId(jwt), workOrderId));
  }

  @PostMapping("/release")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<WorkOrderResponse> markReady(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.markReady(actorId(jwt), workOrderId));
  }

  @PostMapping("/start")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<WorkOrderResponse> markInProgress(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.markInProgress(actorId(jwt), workOrderId));
  }

  @PostMapping("/declare-complete")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<WorkOrderResponse> markWorkCompleted(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.markWorkCompleted(actorId(jwt), workOrderId));
  }

  @PostMapping("/work-logs/{workLogId}/verify")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<WorkLogResponse> verifyWorkLog(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable UUID workLogId) {
    return ApiResponse.success(
        executionService.verifyWorkLog(actorId(jwt), workOrderId, workLogId));
  }

  @GetMapping("/work-logs")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<List<WorkLogResponse>> listWorkLogs(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.listWorkLogs(actorId(jwt), workOrderId));
  }

  @PostMapping("/work-logs")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<WorkLogResponse> recordWork(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @Valid @RequestBody RecordWorkLogRequest request) {
    return ApiResponse.success(executionService.recordWork(actorId(jwt), workOrderId, request));
  }

  @PostMapping("/work-logs/{workLogId}/submit")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<WorkLogResponse> submitWorkLog(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable UUID workLogId) {
    return ApiResponse.success(
        executionService.submitWorkLog(actorId(jwt), workOrderId, workLogId));
  }

  @GetMapping("/changes")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<List<ChangeOrderResponse>> listChanges(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.listChanges(actorId(jwt), workOrderId));
  }

  @PostMapping("/changes")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<ChangeOrderResponse> proposeChange(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @Valid @RequestBody CreateChangeOrderRequest request) {
    return ApiResponse.success(executionService.proposeChange(actorId(jwt), workOrderId, request));
  }

  @PostMapping("/changes/{changeNumber}/approve")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<ChangeOrderResponse> approveChange(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable int changeNumber,
      @RequestBody(required = false) ChangeDecisionRequest request) {
    return ApiResponse.success(
        executionService.decideChange(
            actorId(jwt), workOrderId, changeNumber, request, true, false));
  }

  @PostMapping("/changes/{changeNumber}/reject")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<ChangeOrderResponse> rejectChange(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable int changeNumber,
      @Valid @RequestBody ChangeDecisionRequest request) {
    return ApiResponse.success(
        executionService.decideChange(
            actorId(jwt), workOrderId, changeNumber, request, false, false));
  }

  @PostMapping("/changes/{changeNumber}/return")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<ChangeOrderResponse> returnChange(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable int changeNumber,
      @Valid @RequestBody ChangeDecisionRequest request) {
    return ApiResponse.success(
        executionService.decideChange(
            actorId(jwt), workOrderId, changeNumber, request, false, true));
  }

  @GetMapping("/reports")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<List<ReportVersionResponse>> listReportVersions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.listReportVersions(actorId(jwt), workOrderId));
  }

  @PostMapping("/reports")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<ReportVersionResponse> createReportVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @Valid @RequestBody CreateReportVersionRequest request) {
    return ApiResponse.success(
        executionService.createReportVersion(actorId(jwt), workOrderId, request));
  }

  @PostMapping("/reports/{versionNo}/verify")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<ReportVersionResponse> verifyReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable int versionNo) {
    return ApiResponse.success(executionService.verifyReport(actorId(jwt), workOrderId, versionNo));
  }

  @PostMapping("/reports/{versionNo}/submit")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<ReportVersionResponse> submitReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable int versionNo) {
    return ApiResponse.success(executionService.submitReport(actorId(jwt), workOrderId, versionNo));
  }

  @GetMapping("/acceptance-decisions")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<List<AcceptanceDecisionResponse>> listAcceptanceDecisions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.listAcceptanceDecisions(actorId(jwt), workOrderId));
  }

  /** MF4-18: only the designated independent reviewer can reach this. */
  @PostMapping("/acceptance-decisions")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<AcceptanceDecisionResponse> recordAcceptance(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @Valid @RequestBody RecordAcceptanceRequest request) {
    return ApiResponse.success(
        executionService.recordAcceptance(actorId(jwt), workOrderId, request));
  }

  @GetMapping("/cost-reconciliation")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<CostReconciliationResponse> reconciliationSummary(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.reconciliationSummary(actorId(jwt), workOrderId));
  }

  @PostMapping("/cost-reconciliation")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<CostReconciliationResponse> reconcile(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @Valid @RequestBody ReconcileCostsRequest request) {
    return ApiResponse.success(executionService.reconcile(actorId(jwt), workOrderId, request));
  }

  @PostMapping("/close")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<WorkOrderResponse> close(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(executionService.close(actorId(jwt), workOrderId));
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
