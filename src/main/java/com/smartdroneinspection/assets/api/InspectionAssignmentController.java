package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.RespondToAssignmentRequest;
import com.smartdroneinspection.assets.api.dto.response.AssetPairAssignmentResponse;
import com.smartdroneinspection.assets.service.AssetPairAssignmentService;
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
 * The inspector's view of the assignments an administrator paired them with (MF2-01/02).
 *
 * <p>Only {@code INSPECTOR} reaches these endpoints. The service derives organization scope from
 * the token, so a caller cannot ask for another organization's assignments by supplying ids.
 */
@RestController
@RequestMapping("/api/v1/inspection-assignments")
public class InspectionAssignmentController {

  private final AssetPairAssignmentService assignments;

  public InspectionAssignmentController(AssetPairAssignmentService assignments) {
    this.assignments = assignments;
  }

  @GetMapping("/mine")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<List<AssetPairAssignmentResponse>> listMyUnansweredAssignments(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(assignments.listUnansweredAssignments(subject(jwt)));
  }

  @GetMapping("/{assignmentId}")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<AssetPairAssignmentResponse> getAssignment(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID assignmentId) {
    return ApiResponse.success(assignments.getAssignment(subject(jwt), assignmentId));
  }

  @PostMapping("/{assignmentId}/response")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ApiResponse<AssetPairAssignmentResponse> respond(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID assignmentId,
      @Valid @RequestBody RespondToAssignmentRequest request) {
    return ApiResponse.success(assignments.respond(subject(jwt), assignmentId, request));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
