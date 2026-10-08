package com.smartdroneinspection.maintenance.api;

import com.smartdroneinspection.maintenance.api.dto.request.AssignMaintenanceEngineerRequest;
import com.smartdroneinspection.maintenance.api.dto.request.SubmitWorkLogRequest;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceWorkLogResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceAssignment;
import com.smartdroneinspection.maintenance.service.MaintenanceExecutionService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/maintenance-execution")
public class MaintenanceExecutionController {

  private final MaintenanceExecutionService execution;

  public MaintenanceExecutionController(MaintenanceExecutionService execution) {
    this.execution = execution;
  }

  @PostMapping("/orders/{orderId}/assignments")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('PROVIDER_MANAGER')")
  public ApiResponse<UUID> assignEngineer(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID orderId,
      @Valid @RequestBody AssignMaintenanceEngineerRequest request) {
    UUID assignmentId = execution.assignEngineer(subject(jwt), orderId, request);
    return ApiResponse.success("Maintenance engineer assigned successfully", assignmentId);
  }

  @GetMapping("/my-assignments")
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<List<MaintenanceAssignment>> listMyAssignments(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(execution.listMyAssignments(subject(jwt)));
  }

  @PostMapping(value = "/orders/{orderId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<UUID> uploadEvidence(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID orderId,
      @RequestParam("kind") String kind,
      @RequestPart("file") MultipartFile file) {
    UUID evidenceId = execution.uploadMaintenanceEvidence(subject(jwt), orderId, kind, file);
    return ApiResponse.success("Maintenance evidence uploaded", evidenceId);
  }

  @PostMapping("/orders/{orderId}/work-logs")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('MAINTENANCE_ENGINEER')")
  public ApiResponse<UUID> submitWorkLog(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID orderId,
      @Valid @RequestBody SubmitWorkLogRequest request) {
    UUID logId = execution.submitWorkLog(subject(jwt), orderId, request);
    return ApiResponse.success("Maintenance work log submitted with before/after pair", logId);
  }

  @PostMapping("/work-logs/{workLogId}/verify")
  @PreAuthorize("hasRole('PROVIDER_MANAGER')")
  public ApiResponse<Void> verifyWorkLog(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workLogId) {
    execution.verifyWorkLog(subject(jwt), workLogId);
    return ApiResponse.success("Work log verified and release prepared", null);
  }

  @GetMapping("/tickets/{ticketId}/work-logs")
  @PreAuthorize(
      "hasAnyRole('CLIENT', 'PROVIDER_MANAGER', 'MAINTENANCE_ENGINEER', 'PLATFORM_ADMIN')")
  public ApiResponse<List<MaintenanceWorkLogResponse>> listWorkLogs(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId) {
    return ApiResponse.success(execution.listWorkLogsForTicket(subject(jwt), ticketId));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
