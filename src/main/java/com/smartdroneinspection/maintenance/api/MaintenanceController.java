package com.smartdroneinspection.maintenance.api;

import com.smartdroneinspection.maintenance.api.dto.request.AssignTeamRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateEstimateVersionRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateTaskRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateWorkOrderRequest;
import com.smartdroneinspection.maintenance.api.dto.request.EstimateDecisionRequest;
import com.smartdroneinspection.maintenance.api.dto.response.EstimateVersionResponse;
import com.smartdroneinspection.maintenance.api.dto.response.RepairCandidateResponse;
import com.smartdroneinspection.maintenance.api.dto.response.TaskResponse;
import com.smartdroneinspection.maintenance.api.dto.response.TeamMemberResponse;
import com.smartdroneinspection.maintenance.api.dto.response.WorkOrderPageResponse;
import com.smartdroneinspection.maintenance.api.dto.response.WorkOrderResponse;
import com.smartdroneinspection.maintenance.service.WorkOrderService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MF4 work order endpoints.
 *
 * <p>Each transition is a named action rather than a generic state change. The
 * {@code @PreAuthorize} expressions are navigation policy only; organization, assignment and
 * separation-of-duties scope are enforced in the service, so a caller with the right role but the
 * wrong organization still receives not-found rather than another tenant's data.
 */
@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {

  private final WorkOrderService workOrderService;

  public MaintenanceController(WorkOrderService workOrderService) {
    this.workOrderService = workOrderService;
  }

  @GetMapping("/repair-candidates")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<List<RepairCandidateResponse>> repairCandidates(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(workOrderService.repairCandidates(actorId(jwt)));
  }

  @GetMapping("/work-orders")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<WorkOrderPageResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(workOrderService.list(actorId(jwt), page, size));
  }

  @GetMapping("/work-orders/{workOrderId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<WorkOrderResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(workOrderService.get(actorId(jwt), workOrderId));
  }

  @PostMapping("/work-orders")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<WorkOrderResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateWorkOrderRequest request) {
    return ApiResponse.success(workOrderService.create(actorId(jwt), request));
  }

  @PostMapping("/work-orders/{workOrderId}/team")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<WorkOrderResponse> assignTeam(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @Valid @RequestBody AssignTeamRequest request) {
    return ApiResponse.success(workOrderService.assignTeam(actorId(jwt), workOrderId, request));
  }

  @GetMapping("/work-orders/{workOrderId}/team")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<List<TeamMemberResponse>> team(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(workOrderService.team(actorId(jwt), workOrderId));
  }

  @GetMapping("/work-orders/{workOrderId}/tasks")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<List<TaskResponse>> listTasks(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(workOrderService.listTasks(actorId(jwt), workOrderId));
  }

  @PostMapping("/work-orders/{workOrderId}/tasks")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<TaskResponse> createTask(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @Valid @RequestBody CreateTaskRequest request) {
    return ApiResponse.success(workOrderService.createTask(actorId(jwt), workOrderId, request));
  }

  @GetMapping("/work-orders/{workOrderId}/estimate-versions")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MAINTENANCE_ENGINEER')")
  public ApiResponse<List<EstimateVersionResponse>> listEstimateVersions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workOrderId) {
    return ApiResponse.success(workOrderService.listEstimateVersions(actorId(jwt), workOrderId));
  }

  @PostMapping("/work-orders/{workOrderId}/estimate-versions")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<EstimateVersionResponse> createEstimateVersion(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @Valid @RequestBody CreateEstimateVersionRequest request) {
    return ApiResponse.success(
        workOrderService.createEstimateVersion(actorId(jwt), workOrderId, request));
  }

  @PostMapping("/work-orders/{workOrderId}/estimate-versions/{versionNo}/submit")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<EstimateVersionResponse> submitEstimate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable int versionNo) {
    return ApiResponse.success(
        workOrderService.submitEstimate(actorId(jwt), workOrderId, versionNo));
  }

  @PostMapping("/work-orders/{workOrderId}/estimate-versions/{versionNo}/approve")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<EstimateVersionResponse> approveEstimate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable int versionNo) {
    return ApiResponse.success(
        workOrderService.approveEstimate(actorId(jwt), workOrderId, versionNo));
  }

  @PostMapping("/work-orders/{workOrderId}/estimate-versions/{versionNo}/reject")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<EstimateVersionResponse> rejectEstimate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID workOrderId,
      @PathVariable int versionNo,
      @Valid @RequestBody EstimateDecisionRequest request) {
    return ApiResponse.success(
        workOrderService.rejectEstimate(actorId(jwt), workOrderId, versionNo, request.reason()));
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
