package com.smartdroneinspection.users.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.users.api.dto.request.CreateInspectorFlightLogRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateInspectorProfileRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorFlightLogResponse;
import com.smartdroneinspection.users.api.dto.response.InspectorProfileResponse;
import com.smartdroneinspection.users.service.InspectorProfileService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/platform/inspectors")
public class InspectorProfileController {

  private final InspectorProfileService inspectorProfileService;

  public InspectorProfileController(InspectorProfileService inspectorProfileService) {
    this.inspectorProfileService = inspectorProfileService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "')")
  public ApiResponse<List<InspectorProfileResponse>> listInspectors() {
    return ApiResponse.success(inspectorProfileService.listInspectors());
  }

  @GetMapping("/{inspectorId}")
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "')")
  public ApiResponse<InspectorProfileResponse> getProfile(@PathVariable UUID inspectorId) {
    return ApiResponse.success(inspectorProfileService.getProfile(inspectorId));
  }

  @PutMapping("/{inspectorId}")
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "')")
  public ApiResponse<InspectorProfileResponse> updateProfile(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectorId,
      @Valid @RequestBody UpdateInspectorProfileRequest request) {
    return ApiResponse.success(
        inspectorProfileService.updateProfile(inspectorId, request, subject(jwt)));
  }

  @PostMapping(path = "/{inspectorId}/license-scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "')")
  public ApiResponse<InspectorProfileResponse> uploadLicenseScan(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectorId,
      @RequestParam("file") MultipartFile file) {
    return ApiResponse.success(
        inspectorProfileService.uploadLicenseScan(inspectorId, file, subject(jwt)));
  }

  @GetMapping("/{inspectorId}/license-scan")
  @PreAuthorize(
      "hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "', '" + Roles.INSPECTOR + "')")
  public ResponseEntity<InputStreamResource> viewLicenseScan(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectorId) {
    // If inspector is accessing, ensure they only view their own scan
    if (!hasAnyRole(jwt, Roles.ADMIN, Roles.MANAGER)) {
      if (!subject(jwt).equals(inspectorId)) {
        throw new com.smartdroneinspection.shared.exception.AuthException(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "Inspector may only access their own license scan");
      }
    }
    var content = inspectorProfileService.openLicenseScan(inspectorId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(new InputStreamResource(content.stream()));
  }

  @GetMapping("/{inspectorId}/flight-logs")
  @PreAuthorize(
      "hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "', '" + Roles.INSPECTOR + "')")
  public ApiResponse<List<InspectorFlightLogResponse>> listFlightLogs(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectorId) {
    if (!hasAnyRole(jwt, Roles.ADMIN, Roles.MANAGER)) {
      if (!subject(jwt).equals(inspectorId)) {
        throw new com.smartdroneinspection.shared.exception.AuthException(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "Inspector may only access their own flight logs");
      }
    }
    return ApiResponse.success(inspectorProfileService.listFlightLogs(inspectorId));
  }

  @PostMapping("/{inspectorId}/flight-logs")
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "')")
  public ApiResponse<InspectorFlightLogResponse> recordFlightLog(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectorId,
      @Valid @RequestBody CreateInspectorFlightLogRequest request) {
    return ApiResponse.success(
        inspectorProfileService.recordFlightLog(inspectorId, request, subject(jwt)));
  }

  @DeleteMapping("/{inspectorId}/flight-logs/{logId}")
  @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MANAGER + "')")
  public ApiResponse<Void> deleteFlightLog(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectorId, @PathVariable UUID logId) {
    inspectorProfileService.deleteFlightLog(inspectorId, logId, subject(jwt));
    return ApiResponse.success(null);
  }

  @GetMapping("/me")
  @PreAuthorize("hasRole('" + Roles.INSPECTOR + "')")
  public ApiResponse<InspectorProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(inspectorProfileService.getProfile(subject(jwt)));
  }

  @GetMapping("/me/flight-logs")
  @PreAuthorize("hasRole('" + Roles.INSPECTOR + "')")
  public ApiResponse<List<InspectorFlightLogResponse>> getMyFlightLogs(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(inspectorProfileService.listFlightLogs(subject(jwt)));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  private boolean hasAnyRole(Jwt jwt, String... roles) {
    List<String> userRoles = jwt.getClaimAsStringList("roles");
    if (userRoles == null) return false;
    for (String role : roles) {
      if (userRoles.contains(role)) return true;
    }
    return false;
  }
}
