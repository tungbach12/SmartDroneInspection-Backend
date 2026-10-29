package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.ReviewDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.api.dto.request.SubmitDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneRegistrationApplicationResponse;
import com.smartdroneinspection.assets.api.dto.response.InspectorDroneResponse;
import com.smartdroneinspection.assets.service.DroneRegistrationApplicationService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drone-registration-applications")
public class DroneRegistrationApplicationController {
  private final DroneRegistrationApplicationService applications;

  public DroneRegistrationApplicationController(DroneRegistrationApplicationService applications) {
    this.applications = applications;
  }

  @PostMapping
  @PreAuthorize("hasRole('" + Roles.INSPECTOR + "')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<DroneRegistrationApplicationResponse> submit(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody SubmitDroneRegistrationApplicationRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        applications.submit(
            subject(jwt),
            body,
            request.getRemoteAddr(),
            request.getHeader("User-Agent"),
            request.getHeader("X-Correlation-ID")));
  }

  @GetMapping("/mine")
  @PreAuthorize("hasRole('" + Roles.INSPECTOR + "')")
  public ApiResponse<List<DroneRegistrationApplicationResponse>> mine(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(applications.mine(subject(jwt)));
  }

  @GetMapping("/mine/drones")
  @PreAuthorize("hasRole('" + Roles.INSPECTOR + "')")
  public ApiResponse<List<InspectorDroneResponse>> inspectorDrones(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(applications.inspectorDrones(subject(jwt)));
  }

  @GetMapping("/pending")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<List<DroneRegistrationApplicationResponse>> pending(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(applications.pending(subject(jwt)));
  }

  @PostMapping("/{applicationId}/approve")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<DroneRegistrationApplicationResponse> approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID applicationId,
      HttpServletRequest request) {
    return ApiResponse.success(
        applications.approve(
            subject(jwt),
            applicationId,
            request.getRemoteAddr(),
            request.getHeader("User-Agent"),
            request.getHeader("X-Correlation-ID")));
  }

  @PostMapping("/{applicationId}/reject")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<DroneRegistrationApplicationResponse> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID applicationId,
      @Valid @RequestBody ReviewDroneRegistrationApplicationRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        applications.reject(
            subject(jwt),
            applicationId,
            body,
            request.getRemoteAddr(),
            request.getHeader("User-Agent"),
            request.getHeader("X-Correlation-ID")));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
