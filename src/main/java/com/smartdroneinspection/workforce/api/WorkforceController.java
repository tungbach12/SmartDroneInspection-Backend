package com.smartdroneinspection.workforce.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.workforce.api.dto.request.ProvisionWorkforceUserRequest;
import com.smartdroneinspection.workforce.api.dto.request.UpdateWorkforceRolesRequest;
import com.smartdroneinspection.workforce.api.dto.request.UpdateWorkforceStatusRequest;
import com.smartdroneinspection.workforce.api.dto.response.ProvisionedWorkforceUserResponse;
import com.smartdroneinspection.workforce.api.dto.response.WorkforceUserResponse;
import com.smartdroneinspection.workforce.service.WorkforceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ORG_ADMIN workforce provisioning and lifecycle. The tenant is taken from the authenticated
 * principal inside the service; no endpoint accepts an organization or actor zone.
 */
@RestController
@RequestMapping("/api/v1/workforce/users")
@PreAuthorize("hasRole('" + Roles.ORG_ADMIN + "')")
public class WorkforceController {

  private final WorkforceService workforce;

  public WorkforceController(WorkforceService workforce) {
    this.workforce = workforce;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ProvisionedWorkforceUserResponse>> provision(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ProvisionWorkforceUserRequest body,
      HttpServletRequest request) {
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                workforce.provision(
                    subject(jwt),
                    body,
                    clientIp(request),
                    userAgent(request),
                    correlationId(request))));
  }

  @GetMapping
  public ApiResponse<List<WorkforceUserResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(workforce.list(subject(jwt)));
  }

  @PatchMapping("/{userId}/status")
  public ApiResponse<WorkforceUserResponse> updateStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateWorkforceStatusRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        workforce.updateStatus(
            subject(jwt),
            userId,
            body,
            clientIp(request),
            userAgent(request),
            correlationId(request)));
  }

  @PutMapping("/{userId}/roles")
  public ApiResponse<WorkforceUserResponse> updateRoles(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateWorkforceRolesRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        workforce.updateRoles(
            subject(jwt),
            userId,
            body,
            clientIp(request),
            userAgent(request),
            correlationId(request)));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  private String clientIp(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  private String userAgent(HttpServletRequest request) {
    return request.getHeader("User-Agent");
  }

  private String correlationId(HttpServletRequest request) {
    return request.getHeader("X-Correlation-ID");
  }
}
