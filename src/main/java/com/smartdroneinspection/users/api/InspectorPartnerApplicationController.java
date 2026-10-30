package com.smartdroneinspection.users.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.users.api.dto.request.RejectInspectorPartnerApplicationRequest;
import com.smartdroneinspection.users.api.dto.request.ReviewInspectorPartnerApplicationRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorPartnerApplicationResponse;
import com.smartdroneinspection.users.api.dto.response.InspectorPartnerApprovalResponse;
import com.smartdroneinspection.users.service.InspectorPartnerApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspector-partner-applications")
public class InspectorPartnerApplicationController {
  private final InspectorPartnerApplicationService applications;

  public InspectorPartnerApplicationController(InspectorPartnerApplicationService applications) {
    this.applications = applications;
  }

  @GetMapping("/{applicationId}/license-document")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ResponseEntity<InputStreamResource> licenseDocument(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
    var content = applications.openLicenseDocument(subject(jwt), applicationId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(new InputStreamResource(content.stream()));
  }

  @GetMapping("/pending")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<List<InspectorPartnerApplicationResponse>> pending(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(applications.pending(subject(jwt)));
  }

  @PostMapping("/{applicationId}/approve")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<InspectorPartnerApprovalResponse> approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID applicationId,
      @Valid @RequestBody ReviewInspectorPartnerApplicationRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        applications.approve(
            subject(jwt),
            applicationId,
            body,
            request.getRemoteAddr(),
            request.getHeader("User-Agent"),
            request.getHeader("X-Correlation-ID")));
  }

  @PostMapping("/{applicationId}/reject")
  @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
  public ApiResponse<InspectorPartnerApplicationResponse> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID applicationId,
      @Valid @RequestBody RejectInspectorPartnerApplicationRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        applications.reject(
            subject(jwt),
            applicationId,
            body.reviewNote(),
            request.getRemoteAddr(),
            request.getHeader("User-Agent"),
            request.getHeader("X-Correlation-ID")));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
