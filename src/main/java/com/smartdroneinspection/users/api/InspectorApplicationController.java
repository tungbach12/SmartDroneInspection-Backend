package com.smartdroneinspection.users.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.users.api.dto.request.InspectorApplicationReviewRequest;
import com.smartdroneinspection.users.api.dto.request.InspectorApplicationSubmissionRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorApplicationResponse;
import com.smartdroneinspection.users.api.dto.response.InspectorProfileResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.service.InspectorApplicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inspector-applications")
public class InspectorApplicationController {

  private final InspectorApplicationService service;

  public InspectorApplicationController(InspectorApplicationService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<InspectorApplicationResponse>> submit(
      @Valid @RequestBody InspectorApplicationSubmissionRequest request,
      HttpServletRequest servletRequest) {
    var result =
        service.submit(
            request,
            AuthHttpSupport.clientIp(servletRequest),
            AuthHttpSupport.userAgent(servletRequest),
            AuthHttpSupport.correlationId(servletRequest));
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(ApiResponse.success(result));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('" + Roles.MANAGER + "', '" + Roles.ADMIN + "')")
  public ApiResponse<List<InspectorApplicationResponse>> listPending() {
    return ApiResponse.success(service.listPending());
  }

  @GetMapping("/approved-awaiting-contract")
  @PreAuthorize("hasAnyRole('" + Roles.MANAGER + "', '" + Roles.ADMIN + "')")
  public ApiResponse<List<InspectorApplicationResponse>> listApprovedAwaitingContract() {
    return ApiResponse.success(service.listApprovedAwaitingContract());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('" + Roles.MANAGER + "', '" + Roles.ADMIN + "')")
  public ApiResponse<InspectorApplicationResponse> get(@PathVariable UUID id) {
    return ApiResponse.success(service.get(id));
  }

  @GetMapping("/{id}/license-scan")
  @PreAuthorize("hasAnyRole('" + Roles.MANAGER + "', '" + Roles.ADMIN + "')")
  public ResponseEntity<InputStreamResource> licenseScan(@PathVariable UUID id) {
    var content = service.openLicenseScan(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(new InputStreamResource(content.stream()));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('" + Roles.MANAGER + "', '" + Roles.ADMIN + "')")
  public ApiResponse<UserResponse> approve(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest servletRequest) {
    return ApiResponse.success(
        service.approve(
            id,
            UUID.fromString(jwt.getSubject()),
            AuthHttpSupport.clientIp(servletRequest),
            AuthHttpSupport.userAgent(servletRequest),
            AuthHttpSupport.correlationId(servletRequest)));
  }

  @PostMapping(path = "/license-scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<java.util.Map<String, String>> uploadLicenseScan(
      @RequestParam("file") MultipartFile file) {
    return ApiResponse.success(
        java.util.Map.of("fileKey", service.uploadApplicationLicenseScan(file)));
  }

  @PostMapping("/{id}/cleanup-rejected")
  @PreAuthorize("hasAnyRole('" + Roles.MANAGER + "', '" + Roles.ADMIN + "')")
  public ApiResponse<Void> retryRejectedCleanup(@PathVariable UUID id) {
    service.retryRejectionCleanup(id);
    return ApiResponse.success(null);
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('" + Roles.MANAGER + "', '" + Roles.ADMIN + "')")
  public ApiResponse<Void> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody InspectorApplicationReviewRequest request,
      HttpServletRequest servletRequest) {
    service.reject(
        id,
        UUID.fromString(jwt.getSubject()),
        request.reason(),
        AuthHttpSupport.clientIp(servletRequest),
        AuthHttpSupport.userAgent(servletRequest),
        AuthHttpSupport.correlationId(servletRequest));
    return ApiResponse.success(null);
  }

  @PostMapping(path = "/{id}/signed-contract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('" + Roles.MANAGER + "', '" + Roles.ADMIN + "')")
  public ApiResponse<InspectorProfileResponse> uploadSignedContract(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file) {
    return ApiResponse.success(
        service.uploadSignedContract(id, file, UUID.fromString(jwt.getSubject())));
  }
}
